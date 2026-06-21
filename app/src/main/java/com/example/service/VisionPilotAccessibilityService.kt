package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.content.Context
import android.graphics.PixelFormat
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class AccessibilityNodeData(
    val id: String,
    val className: String,
    val text: String?,
    val bounds: Rect,
    val isClickable: Boolean,
    val isScrollable: Boolean
)

class OverlayBoundingBoxView(context: Context) : View(context) {
    private val paint = Paint().apply {
        color = Color.parseColor("#00FF00")
        strokeWidth = 10f
        style = Paint.Style.STROKE
    }
    private var boundsToDraw = Rect()
    
    fun updateBounds(rect: Rect) {
        boundsToDraw = rect
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!boundsToDraw.isEmpty) {
            canvas.drawRect(boundsToDraw, paint)
        }
    }
}

data class UiElement(
    val className: String,
    val text: String?,
    val centerX: Int,
    val centerY: Int,
    val isClickable: Boolean,
    val isScrollable: Boolean
)

class VisionPilotAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var overlayView: OverlayBoundingBoxView? = null

    companion object {
        private var instance: VisionPilotAccessibilityService? = null

        fun getInstance(): VisionPilotAccessibilityService? {
            return instance
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i("VisionPilotAccService", "Accessibility Service Connected Successfully.")
        setupOverlay()
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    private fun setupOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView = OverlayBoundingBoxView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            Log.e("VisionPilotAccService", "Failed to add overlay", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        try {
            windowManager?.removeView(overlayView)
        } catch(e: Exception) {}
    }
    
    fun highlightNodeBounds(rect: Rect) {
        overlayView?.updateBounds(rect)
    }
    
    fun clearHighlights() {
        overlayView?.updateBounds(Rect())
    }

    /**
     * Extracts UI Hierarchy representing the active screen
     */
    fun extractUiHierarchy(): List<AccessibilityNodeData> {
        val rootNode = rootInActiveWindow ?: return emptyList()
        val nodeList = mutableListOf<AccessibilityNodeData>()
        traverseAndCollect(rootNode, nodeList)
        return nodeList
    }
    
    fun readUiTree(): List<UiElement> {
        return extractUiHierarchy().map { 
            UiElement(
                className = it.className,
                text = it.text,
                centerX = it.bounds.centerX(),
                centerY = it.bounds.centerY(),
                isClickable = it.isClickable,
                isScrollable = it.isScrollable
            )
        }
    }
    
    fun describeAppContent(): String {
        val hierarchy = extractUiHierarchy()
        val descriptions = hierarchy.filter { !it.text.isNullOrBlank() }
            .map { it.text }
            .distinct()
        return if (descriptions.isEmpty()) {
            "There is no readable text on the screen."
        } else {
            "On screen, I see: ${descriptions.joinToString(", ")}"
        }
    }
    
    fun readScreenSequentially(onReadNode: (String) -> Unit, onHighlightNode: (Rect) -> Unit, onComplete: () -> Unit) {
        val hierarchy = extractUiHierarchy()
        val readableNodes = hierarchy.filter { !it.text.isNullOrBlank() }
        
        CoroutineScope(Dispatchers.Main).launch {
            if (readableNodes.isEmpty()) {
                onReadNode("There is no readable text on the screen.")
                delay(3000)
                onComplete()
                return@launch
            }
            
            onReadNode("Reading screen elements.")
            delay(2000)
            
            for (node in readableNodes) {
                onHighlightNode(node.bounds)
                onReadNode(node.text ?: "")
                delay(1500) // Brief delay for reading
            }
            
            clearHighlights()
            onReadNode("Finished reading screen.")
            onComplete()
        }
    }

    fun filterActionableNodes(nodes: List<AccessibilityNodeData>): List<AccessibilityNodeData> {
        return nodes.filter { it.isClickable || it.isScrollable }
    }

    /**
     * Finds a clickable node matching text and clicks it.
     */
    fun clickNodeWithText(textToFind: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val foundNodes = rootNode.findAccessibilityNodeInfosByText(textToFind)
        
        for (node in foundNodes) {
            var currentNode: AccessibilityNodeInfo? = node
            while (currentNode != null) {
                if (currentNode.isClickable) {
                    val result = currentNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return result
                }
                currentNode = currentNode.parent
            }
        }
        return false
    }
    
    fun safePerformClick(nodeId: String): Boolean {
        Log.i("VisionPilotAccService", "Safe action click requested on node component: $nodeId")
        // Just mock for now until we identify node by id exactly. 
        return true
    }

    /**
     * Perform global actions like home, back, etc.
     */
    fun performGlobalActionCode(actionId: Int): Boolean {
        return performGlobalAction(actionId)
    }

    /**
     * Performs a scrolling gesture
     */
    fun performScroll(direction: Int): Boolean {
        Log.i("VisionPilotAccService", "Performing robust scroll.")
        val rootNode = rootInActiveWindow ?: return false
        
        // Let's attempt standard accessibility node scroll first
        var scrollableNode = findFirstScrollableNode(rootNode)
        if (scrollableNode != null) {
            return scrollableNode.performAction(direction)
        }
        
        // Fallback to gesture if node scroll doesn't work (requires API 24+)
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        
        val path = Path()
        if (direction == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
            // Scroll down: Swipe up
            path.moveTo(width / 2f, height * 0.8f)
            path.lineTo(width / 2f, height * 0.2f)
        } else {
            // Scroll up: Swipe down
            path.moveTo(width / 2f, height * 0.2f)
            path.lineTo(width / 2f, height * 0.8f)
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        return dispatchGesture(gesture, null, null)
    }
    
    private fun findFirstScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstScrollableNode(child)
            if (found != null) return found
        }
        return null
    }

    private fun traverseAndCollect(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeData>) {
        val text = node.text?.toString()
        val contentDesc = node.contentDescription?.toString()
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        list.add(
            AccessibilityNodeData(
                id = node.viewIdResourceName ?: "node_${bounds.centerX()}_${bounds.centerY()}",
                className = node.className?.toString() ?: "android.view.View",
                text = text ?: contentDesc,
                bounds = bounds,
                isClickable = node.isClickable,
                isScrollable = node.isScrollable
            )
        )

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                traverseAndCollect(child, list)
            }
        }
    }
}
