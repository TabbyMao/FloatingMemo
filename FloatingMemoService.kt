package com.example.floatingmemo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max

/**
 * Draws a slim semi-transparent line on top of every app. Tap the line to expand
 * it into an editable to-do note; tap "–" to fold it back. Drag to move.
 */
class FloatingMemoService : Service() {

    data class Task(var text: String, var done: Boolean)

    companion object {
        @Volatile var running = false
        private const val CHANNEL_ID = "floating_memo"
        private const val PREFS = "floating_memo_prefs"
        private const val KEY_TASKS = "tasks"
        private const val PANEL_WIDTH_DP = 300
        private const val LIST_MAX_HEIGHT_DP = 340

        // Collapsed handle. Colour is 0xAARRGGBB: the first two hex digits are
        // opacity (99 = 60%). For green use 0x9943A047 instead.
        private const val HANDLE_COLOR = 0x991E88E5.toInt()
        private const val HANDLE_WIDTH_DP = 5
        private const val HANDLE_HEIGHT_DP = 56
    }

    private lateinit var wm: WindowManager
    private lateinit var ui: Context            // themed context so widgets look right
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var root: FrameLayout
    private lateinit var bubble: View           // the collapsed handle (a thin line)
    private lateinit var panel: LinearLayout
    private lateinit var listBox: LinearLayout

    private val tasks = mutableListOf<Task>()
    private var attached = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()

        if (!Settings.canDrawOverlays(this)) {   // permission missing: nothing to show
            stopSelf()
            return
        }

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        ui = ContextThemeWrapper(this, android.R.style.Theme_Material_Light)
        loadTasks()
        buildViews()

        params = WindowManager.LayoutParams(
            WRAP_CONTENT, WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = dp(220)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        wm.addView(root, params)
        attached = true
        running = true
        setExpanded(false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        if (attached) {
            saveTasks()
            wm.removeView(root)
            attached = false
        }
        running = false
        super.onDestroy()
    }

    // ---------------------------------------------------------------- views

    private fun buildViews() {
        root = FrameLayout(ui)

        // Collapsed state: a slim, semi-transparent vertical line. The line itself
        // is thin, but it sits inside a wider invisible area so it is easy to tap.
        val line = View(ui).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(HANDLE_WIDTH_DP).toFloat()
                setColor(HANDLE_COLOR)
            }
            layoutParams = FrameLayout.LayoutParams(
                dp(HANDLE_WIDTH_DP), dp(HANDLE_HEIGHT_DP), Gravity.CENTER
            )
        }
        bubble = FrameLayout(ui).apply {
            addView(line)
            layoutParams = FrameLayout.LayoutParams(dp(24), dp(HANDLE_HEIGHT_DP + 12))
            setOnTouchListener(dragListener { setExpanded(true) })
        }

        // Expanded state: header + scrollable task list + "new task" row.
        panel = LinearLayout(ui).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(0xFFFFF8E1.toInt())
                setStroke(dp(1), 0xFFBCAAA4.toInt())
            }
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }

        val header = LinearLayout(ui).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(ui).apply {
            text = "To-do"
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFF5D4037.toInt())
            setPadding(dp(4), dp(8), dp(4), dp(8))
            setOnTouchListener(dragListener(null))      // drag the note by its title
        }
        val clearDone = headerButton("Clear done") {
            tasks.removeAll { it.done }
            saveTasks()
            rebuildList()
        }
        val fold = headerButton("  –  ") { setExpanded(false) }
        header.addView(title, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        header.addView(clearDone)
        header.addView(fold)

        listBox = LinearLayout(ui).apply { orientation = LinearLayout.VERTICAL }
        val scroll = MaxHeightScrollView(ui, dp(LIST_MAX_HEIGHT_DP)).apply { addView(listBox) }

        val addRow = LinearLayout(ui).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val input = EditText(ui).apply {
            hint = "New task"
            textSize = 15f
            maxLines = 1
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        val addTask = {
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) {
                val task = Task(text, false)
                tasks.add(task)
                listBox.addView(makeRow(task))
                input.setText("")
                saveTasks()
            }
        }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { addTask(); true } else false
        }
        val addButton = headerButton("  ＋  ") { addTask() }.apply { textSize = 20f }
        addRow.addView(input, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        addRow.addView(addButton)

        panel.addView(header)
        panel.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        panel.addView(addRow)

        root.addView(bubble)
        root.addView(panel)
        rebuildList()
    }

    private fun headerButton(label: String, onClick: () -> Unit) = TextView(ui).apply {
        text = label
        textSize = 14f
        setTextColor(0xFF1565C0.toInt())
        setPadding(dp(8), dp(8), dp(8), dp(8))
        setOnClickListener { onClick() }
    }

    private fun rebuildList() {
        listBox.removeAllViews()
        tasks.forEach { listBox.addView(makeRow(it)) }
    }

    /** One task row: checkbox, editable text, delete button. */
    private fun makeRow(task: Task): View {
        val row = LinearLayout(ui).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val edit = EditText(ui).apply {
            setText(task.text)
            textSize = 15f
            background = null
            setTextColor(0xFF333333.toInt())
            setPadding(dp(2), dp(6), dp(2), dp(6))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        applyDoneStyle(edit, task.done)
        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                task.text = s?.toString() ?: ""
                saveTasks()
            }
        })
        val check = CheckBox(ui).apply {
            isChecked = task.done
            // A ticked task stays in the list (struck through) until "Clear done",
            // so an accidental tap is undone by tapping again.
            setOnCheckedChangeListener { _, checked ->
                task.done = checked
                applyDoneStyle(edit, checked)
                saveTasks()
            }
        }
        val delete = TextView(ui).apply {
            text = "✕"
            textSize = 14f
            setTextColor(0xFF9E9E9E.toInt())
            setPadding(dp(10), dp(8), dp(6), dp(8))
            setOnClickListener {
                tasks.remove(task)
                listBox.removeView(row)
                saveTasks()
            }
        }
        row.addView(check)
        row.addView(edit, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        row.addView(delete)
        return row
    }

    private fun applyDoneStyle(edit: EditText, done: Boolean) {
        edit.paintFlags = if (done) edit.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        else edit.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        edit.alpha = if (done) 0.45f else 1f
    }

    // ------------------------------------------------------ expand / collapse

    private fun setExpanded(expanded: Boolean) {
        bubble.visibility = if (expanded) View.GONE else View.VISIBLE
        panel.visibility = if (expanded) View.VISIBLE else View.GONE

        if (expanded) {
            // Focusable so the keyboard works; NOT_TOUCH_MODAL lets taps outside
            // the note still reach the app underneath.
            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            params.width = dp(PANEL_WIDTH_DP)
            val screenWidth = resources.displayMetrics.widthPixels
            params.x = params.x.coerceIn(0, max(0, screenWidth - dp(PANEL_WIDTH_DP)))
        } else {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(root.windowToken, 0)
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            params.width = WRAP_CONTENT
        }
        if (attached) wm.updateViewLayout(root, params)
    }

    /** Moves the whole window while dragging; a tap without movement calls [onTap]. */
    private fun dragListener(onTap: (() -> Unit)?) = object : View.OnTouchListener {
        private val slop = ViewConfiguration.get(this@FloatingMemoService).scaledTouchSlop
        private var startX = 0
        private var startY = 0
        private var downX = 0f
        private var downY = 0f
        private var moved = false

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y
                    downX = e.rawX; downY = e.rawY
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).toInt()
                    val dy = (e.rawY - downY).toInt()
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    if (moved) {
                        params.x = startX + dx
                        params.y = startY + dy
                        wm.updateViewLayout(root, params)
                    }
                }
                MotionEvent.ACTION_UP -> if (!moved) onTap?.invoke()
            }
            return true
        }
    }

    // --------------------------------------------------------------- storage

    private fun loadTasks() {
        tasks.clear()
        val raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_TASKS, null) ?: return
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                tasks.add(Task(o.optString("text"), o.optBoolean("done")))
            }
        } catch (_: Exception) {
            // Corrupt data: start with an empty list rather than crash.
        }
    }

    private fun saveTasks() {
        val arr = JSONArray()
        tasks.forEach { arr.put(JSONObject().put("text", it.text).put("done", it.done)) }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_TASKS, arr.toString()).apply()
    }

    // ---------------------------------------------------- foreground service

    /** Android only keeps a background service alive if it shows a notification. */
    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "FloatingMemo", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("FloatingMemo is running")
            .setContentText("Tap to open settings or stop it")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** ScrollView that grows with its content but never beyond [maxHeightPx]. */
    private class MaxHeightScrollView(context: Context, private val maxHeightPx: Int) :
        ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(
                widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST)
            )
        }
    }
}
