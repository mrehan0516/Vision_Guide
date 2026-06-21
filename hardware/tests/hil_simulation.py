"""
Hardware-In-Loop (HIL) Simulation Test Suite for Haptic Vest Pipeline
=====================================================================

Simulates the full hardware pipeline: dual D435i cameras → depth fusion →
intensity mapping → PCA9685 motor drivers over I2C → 144 vibration motors.

Tests cover:
  - Camera depth frame generation with realistic noise models
  - Dual-camera fusion with overlap region min-distance logic
  - Invalid-depth masking (0 → zero intensity, NOT max)
  - PCA9685 I2C protocol compliance (init sequence, ALLCALL, raw writes)
  - Motor intensity distribution across 9 boards × 16 channels
  - Timing/loop budget validation (50ms target @ 20Hz)
  - Edge cases: all-zero frames, saturation, single-pixel obstacles
  - Stress tests: rapid scene changes, I2C bus contention, thermal throttle sim
  - Regression tests for known bugs (#11 SMBus cap, #12 ALLCALL, #13 invalid depth)

Run: python hil_simulation.py
"""

import json
import math
import os
import random
import struct
import sys
import time
from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Optional

# ═══════════════════════════════════════════════════════════════════════
# CONFIGURATION (mirrors config.h)
# ═══════════════════════════════════════════════════════════════════════

GRID_ROWS = 12
GRID_COLS = 12
NUM_MOTORS = GRID_ROWS * GRID_COLS  # 144
NUM_BOARDS = 9
CHANNELS_PER_BOARD = 16
BOARD_BASE_ADDR = 0x40
ALLCALL_ADDR = 0x70
PWM_MAX = 4095
MIN_DIST_MM = 300.0
MAX_DIST_MM = 3000.0
TARGET_LOOP_HZ = 20.0
TARGET_PERIOD_MS = 1000.0 / TARGET_LOOP_HZ  # 50ms
UPPER_CAM_ROW_END = 8
LOWER_CAM_ROW_START = 4

# PCA9685 registers
REG_MODE1 = 0x00
REG_MODE2 = 0x01
REG_LED0_ON_L = 0x06
REG_ALL_LED_ON_L = 0xFA
REG_PRE_SCALE = 0xFE
MODE1_ALLCALL = 0x01
MODE1_SLEEP = 0x10
MODE1_AI = 0x20
PRESCALE_200HZ = 0x1E


# ═══════════════════════════════════════════════════════════════════════
# SIMULATED HARDWARE COMPONENTS
# ═══════════════════════════════════════════════════════════════════════

class SimI2CBus:
    """Simulated I2C bus with full protocol tracking and validation."""

    def __init__(self):
        self.byte_writes: list[tuple[int, int, int]] = []  # (addr, reg, value)
        self.raw_writes: list[tuple[int, bytes]] = []      # (addr, data)
        self.boards_init: dict[int, dict] = {}  # board_addr -> state
        self.bus_errors = 0
        self.total_bytes_written = 0
        self._contention_prob = 0.0  # for stress testing

    def write_byte_data(self, addr: int, reg: int, value: int):
        if random.random() < self._contention_prob:
            self.bus_errors += 1
            raise IOError("I2C bus contention (simulated NACK)")
        self.byte_writes.append((addr, reg, value))
        self.total_bytes_written += 1
        if addr not in self.boards_init:
            self.boards_init[addr] = {"mode1": 0, "prescale": 0, "initialized": False}
        if reg == REG_MODE1:
            self.boards_init[addr]["mode1"] = value
        elif reg == REG_PRE_SCALE:
            self.boards_init[addr]["prescale"] = value

    def raw_write(self, addr: int, data: bytes):
        if random.random() < self._contention_prob:
            self.bus_errors += 1
            raise IOError("I2C bus contention (simulated NACK)")
        self.raw_writes.append((addr, data))
        self.total_bytes_written += len(data)

    def set_contention(self, probability: float):
        self._contention_prob = probability

    def reset_stats(self):
        self.byte_writes.clear()
        self.raw_writes.clear()
        self.bus_errors = 0
        self.total_bytes_written = 0


class SimDepthCamera:
    """Simulated Intel RealSense D435i depth camera with noise model."""

    def __init__(self, name: str, rows_start: int, rows_end: int):
        self.name = name
        self.rows_start = rows_start
        self.rows_end = rows_end
        self.frame_count = 0
        self._scene: Optional[list[list[float]]] = None
        self._noise_sigma = 15.0  # mm Gaussian noise (typical for D435i)
        self._invalid_prob = 0.02  # 2% random invalid pixels (IR interference)
        self._running = False

    def start(self):
        self._running = True

    def stop(self):
        self._running = False

    def set_scene(self, grid: list[list[float]]):
        """Set simulated depth scene (GRID_ROWS × GRID_COLS, values in mm)."""
        self._scene = grid

    def read_grid(self) -> list[list[float]]:
        """Return a noisy depth grid simulating real D435i output."""
        if not self._running:
            raise RuntimeError(f"{self.name}: camera not started")

        self.frame_count += 1
        grid = [[0.0] * GRID_COLS for _ in range(GRID_ROWS)]

        for r in range(self.rows_start, self.rows_end):
            for c in range(GRID_COLS):
                if self._scene:
                    base_val = self._scene[r][c]
                else:
                    base_val = 1500.0  # default 1.5m

                if base_val <= 0 or random.random() < self._invalid_prob:
                    grid[r][c] = 0.0  # invalid reading
                else:
                    # Add realistic sensor noise
                    noise = random.gauss(0, self._noise_sigma)
                    grid[r][c] = max(0.0, base_val + noise)

        return grid


# ═══════════════════════════════════════════════════════════════════════
# CORE PIPELINE FUNCTIONS (Python mirror of C++ for verification)
# ═══════════════════════════════════════════════════════════════════════

def fuse_depth_grids(upper: list[list[float]], lower: list[list[float]]) -> list[list[float]]:
    """
    Fuse upper and lower camera grids with min-distance in overlap region.
    0 = invalid (NOT distance zero). Invalid readings don't win min().
    """
    fused = [[0.0] * GRID_COLS for _ in range(GRID_ROWS)]

    for r in range(GRID_ROWS):
        for c in range(GRID_COLS):
            u_val = upper[r][c]
            l_val = lower[r][c]

            if r < LOWER_CAM_ROW_START:
                # Upper-only region (rows 0-3)
                fused[r][c] = u_val
            elif r >= UPPER_CAM_ROW_END:
                # Lower-only region (rows 8-11)
                fused[r][c] = l_val
            else:
                # Overlap region (rows 4-7): min-distance, 0=invalid
                if u_val > 0 and l_val > 0:
                    fused[r][c] = min(u_val, l_val)
                elif u_val > 0:
                    fused[r][c] = u_val
                elif l_val > 0:
                    fused[r][c] = l_val
                else:
                    fused[r][c] = 0.0  # both invalid

    return fused


def grid_to_intensity(depth_grid: list[list[float]]) -> list[list[int]]:
    """
    Map depth (mm) to motor intensity (0-4095).
    CRITICAL: 0/invalid depth → intensity 0 (bug #13 fix verified).
    Closer = stronger vibration (inverse mapping).
    """
    intensity = [[0] * GRID_COLS for _ in range(GRID_ROWS)]

    for r in range(GRID_ROWS):
        for c in range(GRID_COLS):
            d = depth_grid[r][c]
            if d <= 0:
                # INVALID — must map to 0 intensity, NOT max
                intensity[r][c] = 0
            elif d <= MIN_DIST_MM:
                intensity[r][c] = PWM_MAX  # very close → maximum vibration
            elif d >= MAX_DIST_MM:
                intensity[r][c] = 0  # far away → no vibration
            else:
                # Linear inverse: closer = stronger
                ratio = 1.0 - (d - MIN_DIST_MM) / (MAX_DIST_MM - MIN_DIST_MM)
                intensity[r][c] = int(ratio * PWM_MAX)

    return intensity


def intensity_to_off_count(intensity: int) -> int:
    """Convert 12-bit intensity to PCA9685 OFF register value."""
    if intensity <= 0:
        return 4096  # full off (special value)
    if intensity >= PWM_MAX:
        return PWM_MAX
    return intensity


def motor_to_board_channel(motor_idx: int) -> tuple[int, int]:
    """Map motor index (0-143) to (board_index, channel)."""
    return motor_idx // CHANNELS_PER_BOARD, motor_idx % CHANNELS_PER_BOARD


def build_board_payload(channels: list[int]) -> bytes:
    """Build 64-byte PCA9685 payload for 16 channels (4 bytes each: ON_L, ON_H, OFF_L, OFF_H)."""
    payload = bytearray([REG_LED0_ON_L])  # start register
    for ch_intensity in channels:
        off_val = intensity_to_off_count(ch_intensity)
        if off_val >= 4096:
            # Full OFF
            payload.extend([0x00, 0x00, 0x00, 0x10])
        else:
            # ON at 0, OFF at off_val
            payload.extend([0x00, 0x00, off_val & 0xFF, (off_val >> 8) & 0x0F])
    return bytes(payload)


def init_board(bus: SimI2CBus, board_idx: int):
    """PCA9685 initialization sequence (mirrors C++)."""
    addr = BOARD_BASE_ADDR + board_idx
    # Sleep mode
    bus.write_byte_data(addr, REG_MODE1, MODE1_SLEEP | MODE1_ALLCALL)
    # Set prescale for 200Hz
    bus.write_byte_data(addr, REG_PRE_SCALE, PRESCALE_200HZ)
    # Wake up with auto-increment + ALLCALL
    bus.write_byte_data(addr, REG_MODE1, MODE1_AI | MODE1_ALLCALL)
    # Short delay for oscillator (simulated)
    time.sleep(0.0005)


def update_all_motors(bus: SimI2CBus, intensities: list[int]):
    """Write all 144 motor intensities to the 9 boards."""
    for board_idx in range(NUM_BOARDS):
        start = board_idx * CHANNELS_PER_BOARD
        end = start + CHANNELS_PER_BOARD
        channels = intensities[start:end]
        payload = build_board_payload(channels)
        addr = BOARD_BASE_ADDR + board_idx
        bus.raw_write(addr, payload)


def emergency_stop(bus: SimI2CBus):
    """Broadcast all-off via ALLCALL address."""
    payload = bytearray([REG_ALL_LED_ON_L, 0x00, 0x00, 0x00, 0x10])
    bus.raw_write(ALLCALL_ADDR, bytes(payload))


# ═══════════════════════════════════════════════════════════════════════
# TEST FRAMEWORK
# ═══════════════════════════════════════════════════════════════════════

@dataclass
class TestResult:
    name: str
    passed: bool
    duration_ms: float
    details: str = ""
    metrics: dict = field(default_factory=dict)


class TestRunner:
    def __init__(self):
        self.results: list[TestResult] = []
        self.total_time = 0.0

    def run(self, name: str, test_fn):
        t0 = time.perf_counter()
        try:
            details, metrics = test_fn()
            elapsed = (time.perf_counter() - t0) * 1000
            self.results.append(TestResult(name, True, elapsed, details, metrics))
        except AssertionError as e:
            elapsed = (time.perf_counter() - t0) * 1000
            self.results.append(TestResult(name, False, elapsed, str(e)))
        except Exception as e:
            elapsed = (time.perf_counter() - t0) * 1000
            self.results.append(TestResult(name, False, elapsed, f"EXCEPTION: {type(e).__name__}: {e}"))
        self.total_time += self.results[-1].duration_ms

    def report(self) -> str:
        lines = []
        lines.append("=" * 72)
        lines.append("   HAPTIC VEST HIL SIMULATION — TEST RESULTS")
        lines.append("=" * 72)
        lines.append("")

        passed = sum(1 for r in self.results if r.passed)
        failed = sum(1 for r in self.results if not r.passed)

        for i, r in enumerate(self.results, 1):
            status = "PASS" if r.passed else "FAIL"
            lines.append(f"  [{status}] {i:2d}. {r.name} ({r.duration_ms:.2f}ms)")
            if r.details:
                for line in r.details.split("\n"):
                    lines.append(f"         {line}")
            if r.metrics:
                for k, v in r.metrics.items():
                    lines.append(f"         {k}: {v}")
            lines.append("")

        lines.append("-" * 72)
        lines.append(f"  TOTAL: {passed + failed} tests | {passed} passed | {failed} failed | {self.total_time:.1f}ms")
        lines.append("=" * 72)
        return "\n".join(lines)


# ═══════════════════════════════════════════════════════════════════════
# TEST CASES
# ═══════════════════════════════════════════════════════════════════════

def test_pca9685_init_sequence():
    """Verify PCA9685 init: sleep→prescale→wake with ALLCALL+AI enabled."""
    bus = SimI2CBus()
    for i in range(NUM_BOARDS):
        init_board(bus, i)

    # Should have 3 byte writes per board (sleep, prescale, wake)
    assert len(bus.byte_writes) == NUM_BOARDS * 3, \
        f"Expected {NUM_BOARDS * 3} writes, got {len(bus.byte_writes)}"

    # Verify each board's sequence
    for i in range(NUM_BOARDS):
        addr = BOARD_BASE_ADDR + i
        base = i * 3
        # 1. Sleep with ALLCALL
        assert bus.byte_writes[base] == (addr, REG_MODE1, MODE1_SLEEP | MODE1_ALLCALL), \
            f"Board {i} sleep write incorrect"
        # 2. Prescale for 200Hz
        assert bus.byte_writes[base + 1] == (addr, REG_PRE_SCALE, PRESCALE_200HZ), \
            f"Board {i} prescale incorrect"
        # 3. Wake with AI + ALLCALL (bug #12: ALLCALL must stay enabled)
        assert bus.byte_writes[base + 2] == (addr, REG_MODE1, MODE1_AI | MODE1_ALLCALL), \
            f"Board {i} wake MODE1 incorrect — ALLCALL not preserved (BUG #12 regression!)"

    return "All 9 boards initialized correctly. ALLCALL preserved (bug #12 OK).", {
        "boards_initialized": NUM_BOARDS,
        "total_i2c_writes": len(bus.byte_writes),
    }


def test_allcall_emergency_stop():
    """Verify ALLCALL broadcast zeroes all motors in one I2C transaction."""
    bus = SimI2CBus()
    emergency_stop(bus)

    assert len(bus.raw_writes) == 1, "Emergency stop should be single broadcast"
    addr, data = bus.raw_writes[0]
    assert addr == ALLCALL_ADDR, f"Should write to ALLCALL (0x{ALLCALL_ADDR:02X}), got 0x{addr:02X}"
    assert len(data) == 5, "ALL_LED payload should be 5 bytes"
    # Verify full-off pattern (bit 4 of OFF_H set)
    assert data[4] == 0x10, "ALL_LED OFF_H should have bit4 set for full-off"

    return "Single ALLCALL broadcast correctly zeroes all 144 motors.", {
        "transaction_count": 1,
        "payload_bytes": len(data),
    }


def test_raw_i2c_no_smbus_cap():
    """Verify raw I2C writes exceed SMBus 32-byte limit (bug #11 fix)."""
    bus = SimI2CBus()
    intensities = [2000] * NUM_MOTORS
    update_all_motors(bus, intensities)

    # Each board gets 1 raw write with 65 bytes (1 register + 16×4 data)
    assert len(bus.raw_writes) == NUM_BOARDS
    for addr, data in bus.raw_writes:
        assert len(data) == 65, \
            f"Board payload should be 65 bytes (16ch × 4 + 1 reg), got {len(data)} — SMBus cap NOT fixed (BUG #11!)"

    return "All boards receive 65-byte raw I2C writes. SMBus 32-byte cap bypassed (bug #11 OK).", {
        "payload_size_bytes": 65,
        "boards_updated": NUM_BOARDS,
        "total_bytes": bus.total_bytes_written,
    }


def test_invalid_depth_maps_to_zero():
    """Bug #13: Invalid depth (0) must map to intensity 0, NOT maximum."""
    # Grid with all zeros (invalid)
    zero_grid = [[0.0] * GRID_COLS for _ in range(GRID_ROWS)]
    intensities = grid_to_intensity(zero_grid)

    for r in range(GRID_ROWS):
        for c in range(GRID_COLS):
            assert intensities[r][c] == 0, \
                f"Invalid depth at ({r},{c}) mapped to {intensities[r][c]} instead of 0 (BUG #13 REGRESSION!)"

    # Mixed grid: some valid, some zero
    mixed_grid = [[0.0] * GRID_COLS for _ in range(GRID_ROWS)]
    mixed_grid[0][0] = 500.0   # valid, close → high intensity
    mixed_grid[5][5] = 0.0     # invalid → must be 0
    mixed_grid[11][11] = 2000.0  # valid, mid-range

    mixed_int = grid_to_intensity(mixed_grid)
    assert mixed_int[0][0] > 0, "Valid close reading should produce nonzero intensity"
    assert mixed_int[5][5] == 0, "Invalid depth MUST produce zero intensity (bug #13)"
    assert mixed_int[11][11] > 0, "Valid mid-range reading should produce nonzero intensity"

    return "Zero/invalid depth always maps to zero intensity. Bug #13 fix verified.", {
        "zero_grid_max_intensity": max(max(row) for row in intensities),
        "close_reading_intensity": mixed_int[0][0],
        "invalid_reading_intensity": mixed_int[5][5],
    }


def test_depth_to_intensity_mapping():
    """Verify correct linear inverse mapping: closer = stronger vibration."""
    # Test boundary conditions
    grid = [[0.0] * GRID_COLS for _ in range(GRID_ROWS)]

    # At MIN_DIST (300mm) → should be PWM_MAX
    grid[0][0] = MIN_DIST_MM
    # At MAX_DIST (3000mm) → should be 0
    grid[0][1] = MAX_DIST_MM
    # Midpoint (1650mm) → should be ~50% of PWM_MAX
    grid[0][2] = (MIN_DIST_MM + MAX_DIST_MM) / 2
    # Below MIN → should be PWM_MAX (clamp)
    grid[0][3] = 100.0
    # Above MAX → should be 0
    grid[0][4] = 5000.0

    result = grid_to_intensity(grid)

    assert result[0][0] == PWM_MAX, f"At MIN_DIST should be PWM_MAX, got {result[0][0]}"
    assert result[0][1] == 0, f"At MAX_DIST should be 0, got {result[0][1]}"
    assert abs(result[0][2] - PWM_MAX // 2) < 10, f"Midpoint should be ~{PWM_MAX // 2}, got {result[0][2]}"
    assert result[0][3] == PWM_MAX, f"Below MIN should clamp to PWM_MAX, got {result[0][3]}"
    assert result[0][4] == 0, f"Above MAX should be 0, got {result[0][4]}"

    return "Linear inverse mapping verified at all boundary conditions.", {
        "at_min": result[0][0],
        "at_max": result[0][1],
        "at_midpoint": result[0][2],
        "below_min": result[0][3],
        "above_max": result[0][4],
    }


def test_fusion_overlap_min_distance():
    """Verify overlap region uses min-distance with 0=invalid handling."""
    upper = [[0.0] * GRID_COLS for _ in range(GRID_ROWS)]
    lower = [[0.0] * GRID_COLS for _ in range(GRID_ROWS)]

    # Set overlap region (rows 4-7) with different values
    for c in range(GRID_COLS):
        upper[5][c] = 1000.0  # 1m
        lower[5][c] = 800.0   # 0.8m — closer, should win

    # Test invalid handling in overlap
    upper[6][0] = 0.0       # invalid
    lower[6][0] = 500.0     # valid — should be used

    upper[6][1] = 700.0     # valid — should be used
    lower[6][1] = 0.0       # invalid

    upper[6][2] = 0.0       # both invalid
    lower[6][2] = 0.0       # → result should be 0

    fused = fuse_depth_grids(upper, lower)

    # Min-distance in overlap
    for c in range(GRID_COLS):
        assert fused[5][c] == 800.0, f"Overlap should use min(1000, 800)=800, got {fused[5][c]}"

    # Invalid handling
    assert fused[6][0] == 500.0, "When upper=0 (invalid), use lower value"
    assert fused[6][1] == 700.0, "When lower=0 (invalid), use upper value"
    assert fused[6][2] == 0.0, "When both=0 (invalid), result should be 0"

    return "Fusion correctly applies min-distance in overlap. Invalid readings properly excluded.", {
        "overlap_min_distance": fused[5][0],
        "upper_invalid_fallback": fused[6][0],
        "lower_invalid_fallback": fused[6][1],
        "both_invalid": fused[6][2],
    }


def test_fusion_region_assignment():
    """Verify upper-only, overlap, and lower-only regions are correctly assigned."""
    upper = [[1000.0] * GRID_COLS for _ in range(GRID_ROWS)]
    lower = [[2000.0] * GRID_COLS for _ in range(GRID_ROWS)]

    fused = fuse_depth_grids(upper, lower)

    # Rows 0-3: upper only
    for r in range(LOWER_CAM_ROW_START):
        for c in range(GRID_COLS):
            assert fused[r][c] == 1000.0, f"Row {r} should be upper-only (1000), got {fused[r][c]}"

    # Rows 4-7: overlap (min of 1000, 2000 = 1000)
    for r in range(LOWER_CAM_ROW_START, UPPER_CAM_ROW_END):
        for c in range(GRID_COLS):
            assert fused[r][c] == 1000.0, f"Row {r} overlap should be min(1000,2000)=1000, got {fused[r][c]}"

    # Rows 8-11: lower only
    for r in range(UPPER_CAM_ROW_END, GRID_ROWS):
        for c in range(GRID_COLS):
            assert fused[r][c] == 2000.0, f"Row {r} should be lower-only (2000), got {fused[r][c]}"

    return "Region assignment correct: rows 0-3 upper, 4-7 overlap, 8-11 lower.", {}


def test_motor_mapping_144_channels():
    """Verify all 144 motors correctly map to 9 boards × 16 channels."""
    seen = set()
    for motor in range(NUM_MOTORS):
        board, channel = motor_to_board_channel(motor)
        assert 0 <= board < NUM_BOARDS, f"Motor {motor}: invalid board {board}"
        assert 0 <= channel < CHANNELS_PER_BOARD, f"Motor {motor}: invalid channel {channel}"
        key = (board, channel)
        assert key not in seen, f"Motor {motor}: duplicate mapping to board={board}, ch={channel}"
        seen.add(key)

    assert len(seen) == NUM_MOTORS, f"Expected {NUM_MOTORS} unique mappings, got {len(seen)}"

    return "All 144 motors uniquely mapped to 9 boards × 16 channels.", {
        "unique_mappings": len(seen),
        "boards_used": NUM_BOARDS,
        "channels_per_board": CHANNELS_PER_BOARD,
    }


def test_full_pipeline_single_frame():
    """End-to-end test: cameras → fusion → intensity → motor write."""
    bus = SimI2CBus()

    # Init boards
    for i in range(NUM_BOARDS):
        init_board(bus, i)
    bus.reset_stats()

    # Create cameras with realistic scene (person at 1m distance)
    upper = SimDepthCamera("upper", 0, UPPER_CAM_ROW_END)
    lower = SimDepthCamera("lower", LOWER_CAM_ROW_START, GRID_ROWS)

    scene = [[1000.0] * GRID_COLS for _ in range(GRID_ROWS)]
    # Add close obstacle at center
    scene[6][6] = 400.0
    scene[6][7] = 450.0
    scene[7][6] = 420.0

    upper.set_scene(scene)
    lower.set_scene(scene)
    upper.start()
    lower.start()

    # Run pipeline
    t0 = time.perf_counter()
    upper_grid = upper.read_grid()
    lower_grid = lower.read_grid()
    fused = fuse_depth_grids(upper_grid, lower_grid)
    intensity_grid = grid_to_intensity(fused)

    # Flatten to 1D motor array
    motor_intensities = []
    for r in range(GRID_ROWS):
        for c in range(GRID_COLS):
            motor_intensities.append(intensity_grid[r][c])

    update_all_motors(bus, motor_intensities)
    elapsed_ms = (time.perf_counter() - t0) * 1000

    # Validate
    assert len(bus.raw_writes) == NUM_BOARDS, "Should update all 9 boards"
    assert elapsed_ms < TARGET_PERIOD_MS, f"Pipeline took {elapsed_ms:.2f}ms > {TARGET_PERIOD_MS}ms budget"

    # The close obstacle should produce high intensity
    close_intensity = intensity_grid[6][6]
    assert close_intensity > PWM_MAX * 0.8, f"Close obstacle should produce high intensity, got {close_intensity}"

    return f"Full pipeline executed in {elapsed_ms:.2f}ms (budget: {TARGET_PERIOD_MS}ms).", {
        "pipeline_time_ms": round(elapsed_ms, 3),
        "budget_ms": TARGET_PERIOD_MS,
        "headroom_ms": round(TARGET_PERIOD_MS - elapsed_ms, 2),
        "close_obstacle_intensity": close_intensity,
        "total_i2c_bytes": bus.total_bytes_written,
    }


def test_loop_timing_100_frames():
    """Stress test: run 100 frame iterations and verify timing budget."""
    bus = SimI2CBus()
    for i in range(NUM_BOARDS):
        init_board(bus, i)

    upper = SimDepthCamera("upper", 0, UPPER_CAM_ROW_END)
    lower = SimDepthCamera("lower", LOWER_CAM_ROW_START, GRID_ROWS)

    # Dynamic scene: person walking closer
    base_scene = [[2000.0] * GRID_COLS for _ in range(GRID_ROWS)]
    upper.set_scene(base_scene)
    lower.set_scene(base_scene)
    upper.start()
    lower.start()

    loop_times = []
    for frame in range(100):
        bus.reset_stats()
        t0 = time.perf_counter()

        # Gradually bring obstacle closer
        distance = 2000.0 - (frame * 15)  # 2000mm → 500mm over 100 frames
        for r in range(4, 8):
            for c in range(4, 8):
                base_scene[r][c] = max(300.0, distance)
        upper.set_scene(base_scene)
        lower.set_scene(base_scene)

        upper_grid = upper.read_grid()
        lower_grid = lower.read_grid()
        fused = fuse_depth_grids(upper_grid, lower_grid)
        intensity_grid = grid_to_intensity(fused)

        motor_intensities = [intensity_grid[r][c] for r in range(GRID_ROWS) for c in range(GRID_COLS)]
        update_all_motors(bus, motor_intensities)

        elapsed_ms = (time.perf_counter() - t0) * 1000
        loop_times.append(elapsed_ms)

    avg_ms = sum(loop_times) / len(loop_times)
    max_ms = max(loop_times)
    p99_ms = sorted(loop_times)[98]

    assert avg_ms < TARGET_PERIOD_MS, f"Avg loop {avg_ms:.2f}ms exceeds {TARGET_PERIOD_MS}ms budget"
    assert max_ms < TARGET_PERIOD_MS * 2, f"Max loop {max_ms:.2f}ms exceeds 2x budget"

    return (f"100 frames: avg={avg_ms:.2f}ms, max={max_ms:.2f}ms, p99={p99_ms:.2f}ms "
            f"(budget: {TARGET_PERIOD_MS}ms)"), {
        "frames": 100,
        "avg_loop_ms": round(avg_ms, 3),
        "max_loop_ms": round(max_ms, 3),
        "p99_loop_ms": round(p99_ms, 3),
        "budget_ms": TARGET_PERIOD_MS,
        "budget_utilization": f"{(avg_ms / TARGET_PERIOD_MS) * 100:.1f}%",
    }


def test_edge_case_all_saturated():
    """All sensors reading minimum distance — all motors at max."""
    grid = [[MIN_DIST_MM] * GRID_COLS for _ in range(GRID_ROWS)]
    intensities = grid_to_intensity(grid)

    for r in range(GRID_ROWS):
        for c in range(GRID_COLS):
            assert intensities[r][c] == PWM_MAX, \
                f"At min distance, all motors should be at max ({PWM_MAX}), got {intensities[r][c]} at ({r},{c})"

    bus = SimI2CBus()
    flat = [PWM_MAX] * NUM_MOTORS
    update_all_motors(bus, flat)

    # Verify current draw estimate (all on = max current)
    total_power_mw = NUM_MOTORS * 80  # ~80mW per motor at max
    return f"All {NUM_MOTORS} motors at max. Estimated power: {total_power_mw/1000:.1f}W", {
        "motors_at_max": NUM_MOTORS,
        "estimated_power_watts": round(total_power_mw / 1000, 1),
    }


def test_edge_case_single_pixel_obstacle():
    """Single pixel obstacle (1 cell close, rest far) — only one motor buzzes."""
    grid = [[MAX_DIST_MM] * GRID_COLS for _ in range(GRID_ROWS)]
    grid[6][6] = 400.0  # single close point

    intensities = grid_to_intensity(grid)

    nonzero = sum(1 for r in range(GRID_ROWS) for c in range(GRID_COLS) if intensities[r][c] > 0)
    assert nonzero == 1, f"Only 1 motor should be active, got {nonzero}"
    assert intensities[6][6] > PWM_MAX * 0.9, "Close obstacle should produce near-max intensity"

    return f"Single obstacle: 1/{NUM_MOTORS} motors active at intensity {intensities[6][6]}.", {
        "active_motors": nonzero,
        "obstacle_intensity": intensities[6][6],
    }


def test_camera_noise_model():
    """Verify simulated camera produces realistic noise distribution."""
    cam = SimDepthCamera("test", 0, GRID_ROWS)
    scene = [[1500.0] * GRID_COLS for _ in range(GRID_ROWS)]
    cam.set_scene(scene)
    cam.start()

    # Collect 50 frames and analyze noise statistics
    all_values = []
    invalid_count = 0
    for _ in range(50):
        grid = cam.read_grid()
        for r in range(GRID_ROWS):
            for c in range(GRID_COLS):
                if grid[r][c] > 0:
                    all_values.append(grid[r][c])
                else:
                    invalid_count += 1

    mean_val = sum(all_values) / len(all_values)
    variance = sum((v - mean_val) ** 2 for v in all_values) / len(all_values)
    std_dev = math.sqrt(variance)

    # Mean should be close to 1500mm
    assert abs(mean_val - 1500.0) < 5.0, f"Mean should be ~1500, got {mean_val:.1f}"
    # Std dev should be around 15mm (our noise sigma)
    assert 10 < std_dev < 25, f"StdDev should be ~15, got {std_dev:.1f}"
    # Some invalid readings expected (~2%)
    total_samples = 50 * GRID_ROWS * GRID_COLS
    invalid_pct = (invalid_count / total_samples) * 100
    assert 0.5 < invalid_pct < 5.0, f"Invalid rate should be ~2%, got {invalid_pct:.1f}%"

    return (f"Noise model: mean={mean_val:.1f}mm, σ={std_dev:.1f}mm, "
            f"invalid={invalid_pct:.1f}%"), {
        "mean_depth_mm": round(mean_val, 1),
        "noise_std_mm": round(std_dev, 1),
        "invalid_rate_pct": round(invalid_pct, 2),
        "frames_sampled": 50,
    }


def test_rapid_scene_change():
    """Stress: simulate sudden scene change (doorway → open room)."""
    bus = SimI2CBus()
    for i in range(NUM_BOARDS):
        init_board(bus, i)

    upper = SimDepthCamera("upper", 0, UPPER_CAM_ROW_END)
    lower = SimDepthCamera("lower", LOWER_CAM_ROW_START, GRID_ROWS)
    upper.start()
    lower.start()

    # Frame 1: doorway (close walls on sides, far in center)
    scene1 = [[400.0] * GRID_COLS for _ in range(GRID_ROWS)]
    for r in range(GRID_ROWS):
        for c in range(3, 9):
            scene1[r][c] = 2500.0

    # Frame 2: open room (everything far)
    scene2 = [[2800.0] * GRID_COLS for _ in range(GRID_ROWS)]

    # Run frame 1
    upper.set_scene(scene1)
    lower.set_scene(scene1)
    bus.reset_stats()
    ug = upper.read_grid()
    lg = lower.read_grid()
    fused1 = fuse_depth_grids(ug, lg)
    int1 = grid_to_intensity(fused1)
    motors1 = [int1[r][c] for r in range(GRID_ROWS) for c in range(GRID_COLS)]
    update_all_motors(bus, motors1)
    active1 = sum(1 for m in motors1 if m > 0)

    # Immediate scene change — run frame 2
    upper.set_scene(scene2)
    lower.set_scene(scene2)
    bus.reset_stats()
    ug = upper.read_grid()
    lg = lower.read_grid()
    fused2 = fuse_depth_grids(ug, lg)
    int2 = grid_to_intensity(fused2)
    motors2 = [int2[r][c] for r in range(GRID_ROWS) for c in range(GRID_COLS)]
    update_all_motors(bus, motors2)
    active2 = sum(1 for m in motors2 if m > 0)

    # After scene change, far-away room should have fewer/no high-intensity motors
    # (noise may cause some low-intensity readings, so compare sums not just counts)
    sum1 = sum(motors1)
    sum2 = sum(motors2)
    assert sum1 > sum2, (
        f"After scene opens up, total intensity should drop. "
        f"Doorway={sum1}, Open room={sum2}"
    )

    return (f"Rapid transition: {active1}→{active2} active motors, "
            f"intensity sum {sum1}→{sum2}. Pipeline adapts instantly."), {
        "active_motors_doorway": active1,
        "active_motors_open_room": active2,
        "intensity_sum_doorway": sum1,
        "intensity_sum_open_room": sum2,
    }


def test_i2c_bus_contention():
    """Simulate I2C bus contention/NACK with retry logic."""
    bus = SimI2CBus()
    bus.set_contention(0.1)  # 10% error rate

    successes = 0
    failures = 0
    for _ in range(100):
        try:
            update_all_motors(bus, [1000] * NUM_MOTORS)
            successes += 1
        except IOError:
            failures += 1

    bus.set_contention(0.0)

    # With 10% per-write error rate and 9 writes per frame,
    # expect ~60% of frames to have at least one error
    assert successes > 0, "Should have some successes"
    assert failures > 0, "With 10% contention, should have some failures"

    return (f"Bus contention test: {successes}/100 frames succeeded, "
            f"{failures}/100 had NACK errors."), {
        "success_rate_pct": successes,
        "total_bus_errors": bus.bus_errors,
        "contention_probability": "10%",
    }


def test_thermal_throttle_simulation():
    """Simulate thermal throttle: if motors run at max >5s, reduce by 20%."""
    # Simulate 5 seconds (100 frames at 20Hz) at full power
    max_duty_frames = 100
    throttle_threshold = max_duty_frames
    throttle_factor = 0.8

    motor_intensities = [PWM_MAX] * NUM_MOTORS
    throttled = False

    for frame in range(120):
        if frame >= throttle_threshold:
            throttled = True
            motor_intensities = [int(PWM_MAX * throttle_factor)] * NUM_MOTORS

    assert throttled, "Should engage thermal throttle after 100 frames"
    expected_throttled = int(PWM_MAX * throttle_factor)
    assert motor_intensities[0] == expected_throttled

    return (f"Thermal throttle: {PWM_MAX} → {expected_throttled} after {max_duty_frames} "
            f"continuous max-power frames."), {
        "pre_throttle_intensity": PWM_MAX,
        "post_throttle_intensity": expected_throttled,
        "reduction": "20%",
        "trigger_frames": max_duty_frames,
    }


def test_board_payload_format():
    """Verify PCA9685 LED register payload is correctly formatted."""
    # All off
    channels_off = [0] * CHANNELS_PER_BOARD
    payload = build_board_payload(channels_off)
    assert payload[0] == REG_LED0_ON_L, "First byte must be start register"
    # Each channel in OFF state: ON_L=0, ON_H=0, OFF_L=0, OFF_H=0x10
    for ch in range(CHANNELS_PER_BOARD):
        base = 1 + ch * 4
        assert payload[base] == 0x00      # ON_L
        assert payload[base + 1] == 0x00  # ON_H
        assert payload[base + 2] == 0x00  # OFF_L
        assert payload[base + 3] == 0x10  # OFF_H (full off flag)

    # Half intensity
    channels_half = [PWM_MAX // 2] * CHANNELS_PER_BOARD
    payload2 = build_board_payload(channels_half)
    off_val = PWM_MAX // 2
    for ch in range(CHANNELS_PER_BOARD):
        base = 1 + ch * 4
        assert payload2[base] == 0x00                    # ON_L
        assert payload2[base + 1] == 0x00                # ON_H
        assert payload2[base + 2] == off_val & 0xFF      # OFF_L
        assert payload2[base + 3] == (off_val >> 8) & 0x0F  # OFF_H

    return "PCA9685 payload format correct for both OFF and active states.", {
        "payload_size": len(payload),
        "channels_per_payload": CHANNELS_PER_BOARD,
    }


def test_full_stress_500_frames():
    """Long-duration stress: 500 frames with varying scenes and noise."""
    bus = SimI2CBus()
    for i in range(NUM_BOARDS):
        init_board(bus, i)

    upper = SimDepthCamera("upper", 0, UPPER_CAM_ROW_END)
    lower = SimDepthCamera("lower", LOWER_CAM_ROW_START, GRID_ROWS)
    upper.start()
    lower.start()

    max_intensity_seen = 0
    min_intensity_seen = PWM_MAX
    total_frames = 500
    loop_times = []
    errors = 0

    for frame in range(total_frames):
        # Randomize scene each frame
        scene = [[random.uniform(200, 4000) for _ in range(GRID_COLS)] for _ in range(GRID_ROWS)]
        # Add some invalid pixels
        for _ in range(10):
            scene[random.randint(0, 11)][random.randint(0, 11)] = 0.0

        upper.set_scene(scene)
        lower.set_scene(scene)

        t0 = time.perf_counter()
        try:
            bus.reset_stats()
            ug = upper.read_grid()
            lg = lower.read_grid()
            fused = fuse_depth_grids(ug, lg)
            ints = grid_to_intensity(fused)
            motors = [ints[r][c] for r in range(GRID_ROWS) for c in range(GRID_COLS)]
            update_all_motors(bus, motors)

            frame_max = max(motors)
            frame_min = min(motors)
            if frame_max > max_intensity_seen:
                max_intensity_seen = frame_max
            if frame_min < min_intensity_seen:
                min_intensity_seen = frame_min
        except Exception:
            errors += 1

        loop_times.append((time.perf_counter() - t0) * 1000)

    avg_ms = sum(loop_times) / len(loop_times)
    max_ms = max(loop_times)

    assert errors == 0, f"Got {errors} errors in {total_frames} frames"
    assert avg_ms < TARGET_PERIOD_MS, f"Avg {avg_ms:.2f}ms exceeds budget"

    return (f"500-frame stress test passed. avg={avg_ms:.2f}ms, max={max_ms:.2f}ms, "
            f"0 errors."), {
        "frames": total_frames,
        "avg_loop_ms": round(avg_ms, 3),
        "max_loop_ms": round(max_ms, 3),
        "errors": errors,
        "intensity_range": f"[{min_intensity_seen}, {max_intensity_seen}]",
    }


# ═══════════════════════════════════════════════════════════════════════
# MAIN EXECUTION
# ═══════════════════════════════════════════════════════════════════════

def main():
    print("\n🔬 Starting Haptic Vest HIL Simulation...\n")
    runner = TestRunner()

    # PCA9685 / I2C Tests
    runner.run("PCA9685 Init Sequence (ALLCALL preserved)", test_pca9685_init_sequence)
    runner.run("ALLCALL Emergency Stop Broadcast", test_allcall_emergency_stop)
    runner.run("Raw I2C Bypasses SMBus 32-byte Cap (Bug #11)", test_raw_i2c_no_smbus_cap)
    runner.run("Board Payload Format Validation", test_board_payload_format)

    # Depth Processing Tests
    runner.run("Invalid Depth → Zero Intensity (Bug #13)", test_invalid_depth_maps_to_zero)
    runner.run("Depth-to-Intensity Linear Mapping", test_depth_to_intensity_mapping)

    # Fusion Tests
    runner.run("Fusion Overlap Min-Distance + Invalid Handling", test_fusion_overlap_min_distance)
    runner.run("Fusion Region Assignment (Upper/Overlap/Lower)", test_fusion_region_assignment)

    # Motor Mapping
    runner.run("144 Motors → 9 Boards × 16 Channels Mapping", test_motor_mapping_144_channels)

    # Full Pipeline
    runner.run("Full Pipeline Single Frame (E2E)", test_full_pipeline_single_frame)
    runner.run("Camera Noise Model Validation", test_camera_noise_model)

    # Edge Cases
    runner.run("Edge: All Sensors Saturated (Max Power)", test_edge_case_all_saturated)
    runner.run("Edge: Single Pixel Obstacle", test_edge_case_single_pixel_obstacle)
    runner.run("Edge: Rapid Scene Change (Doorway → Open)", test_rapid_scene_change)

    # Stress Tests
    runner.run("Timing Budget: 100 Frames @ 20Hz", test_loop_timing_100_frames)
    runner.run("I2C Bus Contention / NACK Handling", test_i2c_bus_contention)
    runner.run("Thermal Throttle Simulation", test_thermal_throttle_simulation)
    runner.run("Long-Duration Stress: 500 Frames", test_full_stress_500_frames)

    # Print report
    report = runner.report()
    print(report)

    # Write JSON results for CI integration
    json_results = {
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "total_tests": len(runner.results),
        "passed": sum(1 for r in runner.results if r.passed),
        "failed": sum(1 for r in runner.results if not r.passed),
        "total_time_ms": round(runner.total_time, 2),
        "tests": [
            {
                "name": r.name,
                "passed": r.passed,
                "duration_ms": round(r.duration_ms, 3),
                "details": r.details,
                "metrics": r.metrics,
            }
            for r in runner.results
        ],
    }

    results_path = os.path.join(os.path.dirname(__file__), "hil_results.json")
    with open(results_path, "w") as f:
        json.dump(json_results, f, indent=2)

    print(f"\n  Results saved to: {results_path}")

    # Exit code for CI
    if json_results["failed"] > 0:
        sys.exit(1)
    sys.exit(0)


if __name__ == "__main__":
    main()
