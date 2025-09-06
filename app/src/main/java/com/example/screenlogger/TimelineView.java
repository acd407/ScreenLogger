package com.example.screenlogger;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 自定义时间线视图，用于显示手机使用时段和未使用时段
 */
public class TimelineView extends View {
    private List<UsagePeriod> usagePeriods = new ArrayList<>();
    private Paint usedPaint;
    private Paint unusedPaint;
    private Paint borderPaint;

    public TimelineView(@NonNull Context context) {
        super(context);
        init();
    }

    public TimelineView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TimelineView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // 初始化画笔
        usedPaint = new Paint();
        usedPaint.setColor(getResources().getColor(android.R.color.holo_green_light));
        usedPaint.setStyle(Paint.Style.FILL);

        unusedPaint = new Paint();
        unusedPaint.setColor(getResources().getColor(android.R.color.darker_gray));
        unusedPaint.setStyle(Paint.Style.FILL);

        // 不再使用单一的borderPaint，而是为不同颜色区域使用不同的边界颜色
    }

    public void setUsagePeriods(List<UsagePeriod> usagePeriods) {
        this.usagePeriods = usagePeriods;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (usagePeriods == null || usagePeriods.isEmpty()) {
            return;
        }

        int width = getWidth();
        int height = getHeight();
        int timelineWidth = width * 3 / 4; // 设置时间轴宽度为视图宽度的3/4，留出右侧空间显示时间
        int timeLabelWidth = width - timelineWidth - 10; // 时间标签区域宽度

        // 找出最早和最晚的时间
        long minTime = Long.MAX_VALUE;
        long maxTime = Long.MIN_VALUE;

        for (UsagePeriod period : usagePeriods) {
            minTime = Math.min(minTime, period.startTime);
            maxTime = Math.max(maxTime, period.endTime);
        }

        if (minTime == maxTime) {
            return; // 避免除零错误
        }

        // 绘制使用时段（改为垂直方向）
        for (UsagePeriod period : usagePeriods) {
            // 计算时段时长（毫秒）
            long duration = period.endTime - period.startTime;
            
            // 忽略时间段过短（<=0）的记录
            if (duration <= 0) {
                continue;
            }
            
            // 计算该时段在视图中的位置（垂直方向）
            float startY = height * (maxTime - period.endTime) / (float) (maxTime - minTime);
            float endY = height * (maxTime - period.startTime) / (float) (maxTime - minTime);
            
            // 计算实际高度
            float periodHeight = endY - startY;
            
            // 时间轴宽度改为现在的一半（从35%减小到17.5%）
            float baseRectWidth = timelineWidth * 0.175f;
            float rectWidth = baseRectWidth;
            float cornerRadius = baseRectWidth / 2; // 圆角半径
            
            // 对于不能正常显示的短时间段，缩小胶囊的大小
            float minHeightForFullDisplay = baseRectWidth; // 能完整显示胶囊形状的最小高度
            if (periodHeight < minHeightForFullDisplay) {
                // 短时间段的胶囊宽度缩小
                rectWidth = Math.max(baseRectWidth * 0.6f, periodHeight);
                cornerRadius = rectWidth / 2;
            }
            
            float rectLeft = (timelineWidth - rectWidth) / 2;
            
            // 绘制圆角矩形（胶囊形状）表示时段
            canvas.drawRoundRect(rectLeft, startY, rectLeft + rectWidth, endY, 
                    cornerRadius, cornerRadius, period.isUsed ? usedPaint : unusedPaint);
            
            // 为绿色部分和灰色部分使用不同的边界颜色
            Paint periodBorderPaint = new Paint();
            periodBorderPaint.setStyle(Paint.Style.STROKE);
            periodBorderPaint.setStrokeWidth(4); // 加粗边框
            
            // 根据时段类型设置不同的边界颜色以实现阴影效果
            if (period.isUsed) {
                // 绿色部分的边界颜色
                periodBorderPaint.setColor(Color.argb(150, 0, 150, 0)); // 半透明深绿色
            } else {
                // 灰色部分的边界颜色
                periodBorderPaint.setColor(Color.argb(150, 100, 100, 100)); // 半透明深灰色
            }
            
            canvas.drawRoundRect(rectLeft, startY, rectLeft + rectWidth, endY, 
                    cornerRadius, cornerRadius, periodBorderPaint);
        }

        // 绘制时间标签（右侧）
        Paint textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(50); // 将时间字体放大到现在的5倍
        textPaint.setTextAlign(Paint.Align.LEFT);

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        
        // 绘制时间轴上的标记（每隔一段时间标记一次）
        long timeRange = maxTime - minTime;
        int timeMarkCount = 5; // 标记的数量
        
        for (int i = 0; i <= timeMarkCount; i++) {
            long markTime = minTime + (timeRange * i / timeMarkCount);
            float markY = height * (maxTime - markTime) / (float) (maxTime - minTime);
            
            // 绘制时间标记线（离时间轴更近）
            Paint linePaint = new Paint();
            linePaint.setColor(Color.GRAY);
            linePaint.setStrokeWidth(1);
            
            // 在时间标签绘制循环中重新计算时间轴的位置参数
            float baseRectWidth = timelineWidth * 0.175f;
            float rectWidth = baseRectWidth;
            float rectLeft = (timelineWidth - rectWidth) / 2;
            
            // 只绘制从时间轴到时间标签起始位置附近的短横线
            float lineStartX = rectLeft + rectWidth + 15; // 从时间轴右侧 + 15像素开始
            float lineEndX = lineStartX + 40; // 短横线长度为40像素
            canvas.drawLine(lineStartX, markY, lineEndX, markY, linePaint);
            
            // 绘制时间文本
            String timeStr = sdf.format(new Date(markTime));
            float textX = lineEndX + 10; // 文本紧接在短横线后面
            // 计算文本的垂直居中位置
            Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();
            float textY = markY - (fontMetrics.ascent + fontMetrics.descent) / 2;
            canvas.drawText(timeStr, textX, textY, textPaint);
        }
    }

    /**
     * 表示一个使用时段
     */
    public static class UsagePeriod {
        long startTime;
        long endTime;
        boolean isUsed;

        public UsagePeriod(long startTime, long endTime, boolean isUsed) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.isUsed = isUsed;
        }
    }
}