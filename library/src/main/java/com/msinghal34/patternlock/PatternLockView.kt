package com.msinghal34.patternlock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.GridLayout
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class PatternLockView : GridLayout {

    companion object {
        const val DEFAULT_RADIUS_RATIO = 0.3f
        const val DEFAULT_LINE_WIDTH = 2f // unit: dp
        const val DEFAULT_SPACING = 24f // unit: dp
        const val DEFAULT_ROW_COUNT = 3
        const val DEFAULT_COLUMN_COUNT = 3
        const val DEFAULT_ERROR_DURATION = 400 // unit: ms
        const val DEFAULT_HIT_AREA_PADDING_RATIO = 0.2f
        const val DEFAULT_INDICATOR_SIZE_RATIO = 0.2f

        const val LINE_STYLE_COMMON = 1
        const val LINE_STYLE_INDICATOR = 2
        const val LINE_STYLE_INVISIBLE = 3
    }

    private var regularCellBackground: Drawable? = null
    private var regularDotColor: Int = 0
    private var regularDotRadiusRatio: Float = 0f

    private var selectedCellBackground: Drawable? = null
    private var selectedDotColor: Int = 0
    private var selectedDotRadiusRatio: Float = 0f

    private var errorCellBackground: Drawable? = null
    private var errorDotColor: Int = 0
    private var errorDotRadiusRatio: Float = 0f

    /**
     * determine the line's style
     * common: 1
     * with indicator: 2
     * invisible: 3
     */
    private var lineStyle: Int = 0

    private var lineWidth: Int = 0
    private var regularLineColor: Int = 0
    private var errorLineColor: Int = 0

    private var spacing: Int = 0

    private var plvRowCount: Int = 0
    private var plvColumnCount: Int = 0

    private var errorDuration: Int = 0
    private var hitAreaPaddingRatio: Float = 0f
    private var indicatorSizeRatio: Float = 0f
    private var vibrate: Boolean = true

    private var cells: ArrayList<Cell> = ArrayList()
    private var selectedCells: ArrayList<Cell> = ArrayList()

    private var linePaint: Paint = Paint()
    private var linePath: Path = Path()

    private var lastX: Float = 0f
    private var lastY: Float = 0f

    private var isSecureMode = false

    private var onPatternListener: OnPatternListener? = null

    constructor(context: Context) : super(context)

    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet) {
        val ta = context.obtainStyledAttributes(attributeSet, R.styleable.PatternLockView)
        regularCellBackground =
            ta.getDrawable(R.styleable.PatternLockView_plv_regularCellBackground)
        regularDotColor = ta.getColor(
            R.styleable.PatternLockView_plv_regularDotColor,
            ContextCompat.getColor(context, R.color.regularColor)
        )
        regularDotRadiusRatio =
            ta.getFloat(R.styleable.PatternLockView_plv_regularDotRadiusRatio, DEFAULT_RADIUS_RATIO)

        selectedCellBackground =
            ta.getDrawable(R.styleable.PatternLockView_plv_selectedCellBackground)
        selectedDotColor = ta.getColor(
            R.styleable.PatternLockView_plv_selectedDotColor,
            ContextCompat.getColor(context, R.color.selectedColor)
        )
        selectedDotRadiusRatio = ta.getFloat(
            R.styleable.PatternLockView_plv_selectedDotRadiusRatio,
            DEFAULT_RADIUS_RATIO
        )

        errorCellBackground = ta.getDrawable(R.styleable.PatternLockView_plv_errorCellBackground)
        errorDotColor = ta.getColor(
            R.styleable.PatternLockView_plv_errorDotColor,
            ContextCompat.getColor(context, R.color.errorColor)
        )
        errorDotRadiusRatio =
            ta.getFloat(R.styleable.PatternLockView_plv_errorDotRadiusRatio, DEFAULT_RADIUS_RATIO)

        lineStyle = ta.getInt(R.styleable.PatternLockView_plv_lineStyle, 1)
        lineWidth = ta.getDimensionPixelSize(
            R.styleable.PatternLockView_plv_lineWidth,
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                DEFAULT_LINE_WIDTH,
                context.resources.displayMetrics
            ).toInt()
        )
        regularLineColor = ta.getColor(
            R.styleable.PatternLockView_plv_regularLineColor,
            ContextCompat.getColor(context, R.color.selectedColor)
        )
        errorLineColor = ta.getColor(
            R.styleable.PatternLockView_plv_errorLineColor,
            ContextCompat.getColor(context, R.color.errorColor)
        )

        spacing = ta.getDimensionPixelSize(
            R.styleable.PatternLockView_plv_spacing,
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                DEFAULT_SPACING,
                context.resources.displayMetrics
            ).toInt()
        )

        plvRowCount = ta.getInteger(R.styleable.PatternLockView_plv_rowCount, DEFAULT_ROW_COUNT)
        plvColumnCount =
            ta.getInteger(R.styleable.PatternLockView_plv_columnCount, DEFAULT_COLUMN_COUNT)

        errorDuration =
            ta.getInteger(R.styleable.PatternLockView_plv_errorDuration, DEFAULT_ERROR_DURATION)
        hitAreaPaddingRatio = ta.getFloat(
            R.styleable.PatternLockView_plv_hitAreaPaddingRatio,
            DEFAULT_HIT_AREA_PADDING_RATIO
        )
        indicatorSizeRatio = ta.getFloat(
            R.styleable.PatternLockView_plv_indicatorSizeRatio,
            DEFAULT_INDICATOR_SIZE_RATIO
        )
        vibrate = ta.getBoolean(R.styleable.PatternLockView_plv_vibrate, true)

        ta.recycle()

        rowCount = plvRowCount
        columnCount = plvColumnCount

        setupCells()
        initPathPaint()
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                reset()
                val hitCell = getHitCell(event.x.toInt(), event.y.toInt())
                if (hitCell == null) {
                    return false
                } else {
                    onPatternListener?.onStarted()
                    notifyCellSelected(hitCell)
                }
            }

            MotionEvent.ACTION_MOVE -> handleActionMove(event)

            MotionEvent.ACTION_UP -> onFinish()

            MotionEvent.ACTION_CANCEL -> reset()

            else -> return false
        }
        return true
    }

    private fun handleActionMove(event: MotionEvent) {
        val hitCell = getHitCell(event.x.toInt(), event.y.toInt())

        if ((hitCell != null) && (!selectedCells.contains(hitCell))) {
            // Notify selection of any previously unselected cells which lie on this line.
            // Corresponding logic is only applicable for a simple case when both number of rows and columns are 3.
            if ((selectedCells.size > 0) && (plvRowCount == 3) && (plvColumnCount == 3)) {
                val lastCell = selectedCells.last()
                val potentialIndex = (lastCell.index + hitCell.index) / 2
                if (!selectedCells.contains(cells[potentialIndex]) && (abs(lastCell.index / 3 - hitCell.index / 3) % 2 == 0) && (abs(
                        lastCell.index % 3 - hitCell.index % 3
                    ) % 2 == 0)
                ) {
                    notifyCellSelected(cells[potentialIndex])
                }
            }

            notifyCellSelected(hitCell)
        }

        lastX = event.x
        lastY = event.y

        invalidate()
    }

    private fun notifyCellSelected(cell: Cell) {
        selectedCells.add(cell)
        onPatternListener?.onProgress(generateSelectedIds())
        maybeVibrate(HapticFeedbackConstants.VIRTUAL_KEY)
        if (isSecureMode) return
        cell.setState(State.SELECTED)
        val center = cell.getCenter()
        if (selectedCells.size == 1) {
            if (lineStyle == LINE_STYLE_COMMON) {
                linePath.moveTo(center.x.toFloat(), center.y.toFloat())
            }
        } else {
            if (lineStyle == LINE_STYLE_COMMON) {
                linePath.lineTo(center.x.toFloat(), center.y.toFloat())
            } else if (lineStyle == LINE_STYLE_INDICATOR) {
                val previousCell = selectedCells[selectedCells.size - 2]
                val previousCellCenter = previousCell.getCenter()
                val diffX = center.x - previousCellCenter.x
                val diffY = center.y - previousCellCenter.y
                val radius = cell.getRadius()
                val length = sqrt((diffX * diffX + diffY * diffY).toDouble())

                linePath.moveTo(
                    (previousCellCenter.x + radius * diffX / length).toFloat(),
                    (previousCellCenter.y + radius * diffY / length).toFloat()
                )
                linePath.lineTo(
                    (center.x - radius * diffX / length).toFloat(),
                    (center.y - radius * diffY / length).toFloat()
                )

                val degree = Math.toDegrees(atan2(diffY.toDouble(), diffX.toDouble())) + 90
                previousCell.setDegree(degree.toFloat())
                previousCell.invalidate()
            }
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (isSecureMode) return
        canvas.drawPath(linePath, linePaint)
        if (selectedCells.size > 0 && lastX > 0 && lastY > 0) {
            if (lineStyle == LINE_STYLE_COMMON) {
                val center = selectedCells[selectedCells.size - 1].getCenter()
                canvas.drawLine(center.x.toFloat(), center.y.toFloat(), lastX, lastY, linePaint)
            } else if (lineStyle == LINE_STYLE_INDICATOR) {
                val lastCell = selectedCells[selectedCells.size - 1]
                val lastCellCenter = lastCell.getCenter()
                val radius = lastCell.getRadius()

                if (!(lastX >= lastCellCenter.x - radius &&
                            lastX <= lastCellCenter.x + radius &&
                            lastY >= lastCellCenter.y - radius &&
                            lastY <= lastCellCenter.y + radius)
                ) {
                    val diffX = lastX - lastCellCenter.x
                    val diffY = lastY - lastCellCenter.y
                    val length = sqrt((diffX * diffX + diffY * diffY).toDouble())
                    canvas.drawLine(
                        (lastCellCenter.x + radius * diffX / length).toFloat(),
                        (lastCellCenter.y + radius * diffY / length).toFloat(),
                        lastX, lastY, linePaint
                    )
                }
            }
        }
    }

    private fun setupCells() {
        for (i in 0..(plvRowCount - 1)) {
            for (j in 0..(plvColumnCount - 1)) {
                val cell = Cell(
                    context, i * plvColumnCount + j,
                    regularCellBackground, regularDotColor, regularDotRadiusRatio,
                    selectedCellBackground, selectedDotColor, selectedDotRadiusRatio,
                    errorCellBackground, errorDotColor, errorDotRadiusRatio,
                    lineStyle, regularLineColor, errorLineColor, plvColumnCount, indicatorSizeRatio
                )
                val cellPadding = spacing / 2
                cell.setPadding(cellPadding, cellPadding, cellPadding, cellPadding)
                addView(cell)

                cells.add(cell)
            }
        }
    }

    private fun initPathPaint() {
        linePaint.isAntiAlias = true
        linePaint.isDither = true
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeJoin = Paint.Join.ROUND
        linePaint.strokeCap = Paint.Cap.ROUND

        linePaint.strokeWidth = lineWidth.toFloat()
        linePaint.color = regularLineColor
    }

    private fun reset() {
        for (cell in selectedCells) {
            cell.reset()
        }
        selectedCells.clear()
        linePaint.color = regularLineColor
        linePath.reset()

        lastX = 0f
        lastY = 0f

        invalidate()
    }

    fun enableSecureMode() {
        isSecureMode = true
    }

    fun disableSecureMode() {
        isSecureMode = false
    }

    fun enableVibration() {
        vibrate = true
    }

    fun disableVibration() {
        vibrate = false
    }

    fun setLineStyle(lineStyle: Int) {
        this.lineStyle = lineStyle
    }

    private fun getHitCell(x: Int, y: Int): Cell? {
        for (cell in cells) {
            if (isSelected(cell, x, y)) {
                return cell
            }
        }
        return null
    }

    private fun isSelected(view: View, x: Int, y: Int): Boolean {
        var innerPadding = view.width * hitAreaPaddingRatio
        return x >= view.left + innerPadding &&
                x <= view.right - innerPadding &&
                y >= view.top + innerPadding &&
                y <= view.bottom - innerPadding
    }

    private fun onFinish() {
        lastX = 0f
        lastY = 0f

        val isCorrect = onPatternListener?.onComplete(generateSelectedIds())
        if (isCorrect != null && isCorrect) {
            reset()
        } else {
            maybeVibrate(HapticFeedbackConstants.LONG_PRESS)
            onError()
        }
    }

    private fun generateSelectedIds(): ArrayList<Int> {
        var result = ArrayList<Int>()
        for (cell in selectedCells) {
            result.add(cell.index)
        }
        return result
    }

    private fun onError() {
        if (isSecureMode) {
            reset()
            return
        }
        for (cell in selectedCells) {
            cell.setState(State.ERROR)
        }
        linePaint.color = errorLineColor
        invalidate()

        postDelayed({
            reset()
        }, errorDuration.toLong())

    }

    private fun maybeVibrate(keyCode: Int) {
        if (vibrate) {
            try {
                performHapticFeedback(keyCode, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
            } catch (e: Exception) {
                Log.e("PatternLockView", "Exception while trying to vibrate", e)
            }
        }
    }

    fun setOnPatternListener(listener: OnPatternListener) {
        onPatternListener = listener
    }

    interface OnPatternListener {
        fun onStarted() {}
        fun onProgress(ids: ArrayList<Int>) {}
        fun onComplete(ids: ArrayList<Int>): Boolean
    }
}
