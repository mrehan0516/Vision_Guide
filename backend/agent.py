"""
agent.py — Agentic flow engine for VisionPilot.

Takes a high-level user goal (voice or text), decomposes it into
executable steps, runs them sequentially against the phone's screen
context, and narrates every action back to the user via TTS.

The agent can:
  - Plan multi-step goals ("search Google for a Reddit post about capybara
    then sign in using this account")
  - Route the phone: open apps, tap UI elements, type text, scroll, go back
  - Observe the screen after each action and decide the next step
  - Recover from unexpected screens (popups, permission dialogs, errors)
  - Report what it's doing at every step (voice-controllable narration)
"""

from __future__ import annotations

import json
import logging
import time
from dataclasses import dataclass, field
from enum import Enum, auto
from typing import Any, Optional

log = logging.getLogger("agent")


# ─── Action vocabulary the agent can emit ────────────────────────────

class ActionType(str, Enum):
    TAP = "tap"
    LONG_PRESS = "long_press"
    TYPE_TEXT = "type_text"
    SCROLL_DOWN = "scroll_down"
    SCROLL_UP = "scroll_up"
    SWIPE_LEFT = "swipe_left"
    SWIPE_RIGHT = "swipe_right"
    PRESS_BACK = "press_back"
    PRESS_HOME = "press_home"
    OPEN_APP = "open_app"
    WAIT = "wait"
    DONE = "done"
    FAIL = "fail"


@dataclass
class AgentAction:
    """A single atomic action the agent wants the phone to perform."""
    action: ActionType
    target: str = ""          # element label / selector to tap
    value: str = ""           # text to type, app name to open, etc.
    narration: str = ""       # what to tell the user via TTS
    x: int = 0                # tap coordinates (used when target is coords)
    y: int = 0
    confidence: float = 1.0


@dataclass
class StepResult:
    """What the phone reports back after executing an action."""
    success: bool
    screen_description: str = ""
    elements_json: str = "[]"
    error: str = ""


@dataclass
class AgentPlan:
    """High-level plan the agent creates from a user goal."""
    goal: str
    steps: list[str] = field(default_factory=list)
    current_step: int = 0
    status: str = "planning"  # planning | executing | completed | failed


# ─── Gemini-backed planning and reasoning ────────────────────────────

PLAN_SYSTEM_PROMPT = """\
You are the AI brain of VisionPilot, a smartphone assistant for visually \
impaired users. You control an Android phone through screen actions.

Given a user's goal, produce a JSON plan with concrete steps.
Each step should be a short imperative sentence describing one screen action.

Rules:
- Be specific: "Tap the search bar at the top" not "search"
- Include navigation: "Open Chrome browser" before "Type in the URL bar"
- Account for common popups: "Dismiss cookie banner if present"
- Keep steps atomic: one tap/type/scroll per step
- Maximum 15 steps

Output format (strict JSON, no markdown):
{"goal": "...", "steps": ["step 1", "step 2", ...]}
"""

STEP_SYSTEM_PROMPT = """\
You are VisionPilot's action engine. You receive:
1. The current goal and which step you're on
2. The current screen state (UI elements with labels and positions)
3. The step instruction to execute

Decide the SINGLE best action to perform. Pick from:
  tap, long_press, type_text, scroll_down, scroll_up, swipe_left,
  swipe_right, press_back, press_home, open_app, wait, done, fail

Output strict JSON (no markdown):
{
  "action": "tap|type_text|...",
  "target": "element label or description to interact with",
  "value": "text to type or app name (if applicable, else empty string)",
  "narration": "short sentence telling the user what you're doing",
  "x": 0, "y": 0,
  "confidence": 0.95
}

If the goal is complete, use action "done".
If something is wrong and you can't proceed, use action "fail" and explain in narration.
"""


class VisionAgent:
    """
    Agentic controller that turns voice commands into multi-step
    phone automation sequences.

    Plugs into any LLM backend via the `llm_call` callback, but
    defaults to Gemini via google.generativeai.
    """

    def __init__(self, gemini_model=None, api_key: str = ""):
        self._model = None
        self._genai = None
        self._setup_gemini(gemini_model, api_key)
        self.current_plan: Optional[AgentPlan] = None
        self.action_history: list[AgentAction] = []
        self.max_retries_per_step = 3
        self.max_total_actions = 30  # safety cap

    def _setup_gemini(self, model, api_key: str):
        try:
            import google.generativeai as genai
            self._genai = genai
            if api_key:
                genai.configure(api_key=api_key)
            self._model = model or genai.GenerativeModel(
                "gemini-2.0-flash",
                generation_config={"temperature": 0.2, "max_output_tokens": 1024},
            )
        except ImportError:
            log.warning("google-generativeai not installed — agent will use fallback planning")

    # ── Planning ─────────────────────────────────────────────────

    def plan(self, user_goal: str) -> AgentPlan:
        """Decompose a user goal into ordered steps."""
        log.info("Planning goal: %s", user_goal)

        if self._model:
            try:
                resp = self._model.generate_content(
                    [PLAN_SYSTEM_PROMPT, f"User goal: {user_goal}"]
                )
                raw = resp.text.strip().replace("```json", "").replace("```", "")
                data = json.loads(raw)
                plan = AgentPlan(
                    goal=data.get("goal", user_goal),
                    steps=data.get("steps", []),
                    status="executing",
                )
                self.current_plan = plan
                log.info("Plan created with %d steps", len(plan.steps))
                return plan
            except Exception as e:
                log.error("Gemini planning failed: %s — using fallback", e)

        # Fallback: simple heuristic plan
        plan = self._fallback_plan(user_goal)
        self.current_plan = plan
        return plan

    def _fallback_plan(self, goal: str) -> AgentPlan:
        """Rule-based fallback when LLM is unavailable."""
        goal_lower = goal.lower()
        steps: list[str] = []

        if "open" in goal_lower:
            app = goal_lower.split("open")[-1].strip()
            steps = [f"Open the {app} app", f"Wait for {app} to load"]
        elif "search" in goal_lower and "google" in goal_lower:
            query = goal_lower.split("for")[-1].strip() if "for" in goal_lower else "query"
            steps = [
                "Open Chrome browser",
                "Tap the search/URL bar at the top",
                f"Type: {query}",
                "Tap the search/go button on keyboard",
                "Wait for search results to load",
            ]
        elif "call" in goal_lower:
            contact = goal_lower.split("call")[-1].strip()
            steps = [
                "Open the Phone app",
                f"Search for contact: {contact}",
                f"Tap on {contact} in the results",
                "Tap the call button",
            ]
        elif "send" in goal_lower and ("message" in goal_lower or "whatsapp" in goal_lower):
            steps = [
                "Open WhatsApp",
                "Tap on the target chat",
                "Tap the message input field",
                "Type the message",
                "Tap the send button",
            ]
        else:
            steps = [
                "Analyze current screen",
                f"Attempt to fulfill: {goal}",
            ]

        return AgentPlan(goal=goal, steps=steps, status="executing")

    # ── Step execution ───────────────────────────────────────────

    def decide_action(self, screen_elements_json: str, screenshot_description: str = "") -> AgentAction:
        """
        Given the current screen state and the current step in the plan,
        decide the next atomic action.
        """
        if not self.current_plan or self.current_plan.status not in ("executing",):
            return AgentAction(action=ActionType.DONE, narration="No active plan.")

        plan = self.current_plan
        if plan.current_step >= len(plan.steps):
            plan.status = "completed"
            return AgentAction(action=ActionType.DONE, narration=f"Goal completed: {plan.goal}")

        if len(self.action_history) >= self.max_total_actions:
            plan.status = "failed"
            return AgentAction(action=ActionType.FAIL, narration="Safety limit reached. Stopping.")

        step_instruction = plan.steps[plan.current_step]
        log.info("Step %d/%d: %s", plan.current_step + 1, len(plan.steps), step_instruction)

        if self._model:
            try:
                context = (
                    f"Goal: {plan.goal}\n"
                    f"Step {plan.current_step + 1}/{len(plan.steps)}: {step_instruction}\n"
                    f"Screen description: {screenshot_description}\n"
                    f"UI elements on screen:\n{screen_elements_json}"
                )
                resp = self._model.generate_content(
                    [STEP_SYSTEM_PROMPT, context]
                )
                raw = resp.text.strip().replace("```json", "").replace("```", "")
                data = json.loads(raw)
                action = AgentAction(
                    action=ActionType(data.get("action", "wait")),
                    target=data.get("target", ""),
                    value=data.get("value", ""),
                    narration=data.get("narration", step_instruction),
                    x=data.get("x", 0),
                    y=data.get("y", 0),
                    confidence=data.get("confidence", 0.8),
                )
                self.action_history.append(action)
                return action
            except Exception as e:
                log.error("Gemini step reasoning failed: %s — using fallback", e)

        # Fallback action from step text
        action = self._fallback_action(step_instruction, screen_elements_json)
        self.action_history.append(action)
        return action

    def _fallback_action(self, step: str, elements_json: str) -> AgentAction:
        """Heuristic action when LLM is unavailable."""
        step_lower = step.lower()

        if step_lower.startswith("open"):
            app = step.split("Open")[-1].split("the")[-1].strip().rstrip(" app")
            return AgentAction(
                action=ActionType.OPEN_APP,
                value=app,
                narration=f"Opening {app}",
            )
        elif step_lower.startswith("type") or step_lower.startswith("enter"):
            text = step.split(":", 1)[-1].strip() if ":" in step else step.split("Type")[-1].strip()
            return AgentAction(
                action=ActionType.TYPE_TEXT,
                value=text,
                narration=f"Typing: {text}",
            )
        elif "tap" in step_lower or "click" in step_lower or "press" in step_lower:
            target = step
            # Try to find a matching element from the screen
            try:
                elements = json.loads(elements_json) if elements_json else []
                for el in elements:
                    label = el.get("label", "").lower()
                    if any(word in label for word in step_lower.split() if len(word) > 3):
                        return AgentAction(
                            action=ActionType.TAP,
                            target=el.get("label", target),
                            x=(el.get("left", 0) + el.get("right", 0)) // 2,
                            y=(el.get("top", 0) + el.get("bottom", 0)) // 2,
                            narration=f"Tapping on {el.get('label', target)}",
                        )
            except (json.JSONDecodeError, TypeError):
                pass
            return AgentAction(
                action=ActionType.TAP,
                target=target,
                narration=f"Tapping: {target}",
            )
        elif "scroll" in step_lower:
            direction = ActionType.SCROLL_DOWN if "down" in step_lower else ActionType.SCROLL_UP
            return AgentAction(action=direction, narration="Scrolling the screen")
        elif "wait" in step_lower:
            return AgentAction(action=ActionType.WAIT, narration="Waiting for the screen to update")
        elif "back" in step_lower:
            return AgentAction(action=ActionType.PRESS_BACK, narration="Going back")
        elif "search" in step_lower:
            query = step.split(":")[-1].strip() if ":" in step else step.split("for")[-1].strip()
            return AgentAction(
                action=ActionType.TYPE_TEXT,
                value=query,
                narration=f"Searching for: {query}",
            )
        else:
            return AgentAction(
                action=ActionType.WAIT,
                narration=f"Analyzing: {step}",
            )

    def advance_step(self, result: StepResult) -> AgentAction:
        """
        Called after the phone executes an action. Evaluates the result,
        advances the plan, and returns the next action.
        """
        if not self.current_plan:
            return AgentAction(action=ActionType.DONE, narration="No plan active.")

        plan = self.current_plan

        if result.success:
            plan.current_step += 1
            if plan.current_step >= len(plan.steps):
                plan.status = "completed"
                return AgentAction(
                    action=ActionType.DONE,
                    narration=f"All done! {plan.goal} completed successfully.",
                )
            return self.decide_action(result.elements_json, result.screen_description)
        else:
            log.warning("Step failed: %s", result.error)
            # Retry logic: try the same step again with updated screen context
            return self.decide_action(result.elements_json, result.screen_description)

    def get_status_narration(self) -> str:
        """Return a human-readable status for TTS."""
        if not self.current_plan:
            return "No task in progress. Tell me what you'd like to do."
        plan = self.current_plan
        if plan.status == "completed":
            return f"Task completed: {plan.goal}"
        elif plan.status == "failed":
            return f"Task failed: {plan.goal}. Please try again or give me a different command."
        else:
            step_num = plan.current_step + 1
            total = len(plan.steps)
            current = plan.steps[plan.current_step] if plan.current_step < total else "finishing up"
            return f"Step {step_num} of {total}: {current}"

    def reset(self):
        """Clear current plan and history."""
        self.current_plan = None
        self.action_history.clear()
