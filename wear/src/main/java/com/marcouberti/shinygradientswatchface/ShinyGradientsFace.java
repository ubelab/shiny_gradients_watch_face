package com.marcouberti.shinygradientswatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.marcouberti.shinygradientswatchface.utils.ScreenUtils;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class ShinyGradientsFace extends CanvasWatchFaceService {

    private static final String TAG = "NatureGradientsFace";

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = 1000;

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    Shader shader;
    int selectedGradient;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{
        Paint mBackgroundPaint;
        //Paint mHandPaint;
        Paint mDatePaint;
        Paint mSecondsCirclePaint;
        Paint darkGrayFillPaint,whiteFillPaint, blackFillPaint;
        Paint circleStrokePaint,complicationsCircleStrokePaint;
        boolean mAmbient;
        Calendar mCalendar;
        Time mTime;
        boolean mIsRound =false;

        final Handler mUpdateTimeHandler = new EngineHandler(this);

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(ShinyGradientsFace.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        boolean mRegisteredTimeZoneReceiver = false;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);
            mIsRound = insets.isRound();
            /*
            if(mIsRound) {
                mHandPaint.setTextSize(getResources().getDimension(R.dimen.font_size_time_round));
            }else{
                mHandPaint.setTextSize(getResources().getDimension(R.dimen.font_size_time_square));
            }
            */
        }

        int INFO_DETAILS_MODE = 0;
        int SECONDS_MODE = 0;
        @Override
        public void onTapCommand(@TapType int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case WatchFaceService.TAP_TYPE_TAP:
                    handleTouch(x,y);
                    invalidate();
                    break;

                case WatchFaceService.TAP_TYPE_TOUCH:
                    break;
                case WatchFaceService.TAP_TYPE_TOUCH_CANCEL:
                    break;

                default:
                    super.onTapCommand(tapType, x, y, eventTime);
                    break;
            }
        }

        private void handleTouch(int x, int y) {
            int W = ScreenUtils.getScreenWidth(getApplicationContext());
            int H = ScreenUtils.getScreenHeight(getApplicationContext());

            //CENTER TO CHANGE THEME
            if(x <(W/2 + W/4) && x >(W/4)) {
                if(y <(H/2 + H/4) && y >(H/4)) {
                    handleCenterTouch();
                }
                //ELSE SECOND INDICATOR TOGGLE
                else {
                    handleOutsideTouch();
                }
            }
            //ELSE SECOND INDICATOR TOGGLE
            else {
                handleOutsideTouch();
            }
        }

        private void handleCenterTouch(){
            //switch between themes
            if(INFO_DETAILS_MODE == 0) INFO_DETAILS_MODE =1;
            else INFO_DETAILS_MODE = 0;
        }

        private void handleOutsideTouch() {
            if(SECONDS_MODE == 0) SECONDS_MODE =1;
            else SECONDS_MODE = 0;
        }

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(ShinyGradientsFace.this)
                    .setAcceptsTapEvents(true)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false).
                    setViewProtectionMode(WatchFaceStyle.PROTECT_STATUS_BAR)
                    .build());

            Resources resources = ShinyGradientsFace.this.getResources();

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setAntiAlias(true);
            mBackgroundPaint.setColor(resources.getColor(R.color.time_date_color));

            mSecondsCirclePaint= new Paint();
            mSecondsCirclePaint.setAntiAlias(true);
            mSecondsCirclePaint.setStyle(Paint.Style.FILL_AND_STROKE);
            mSecondsCirclePaint.setColor(Color.WHITE);
            mSecondsCirclePaint.setStrokeWidth(ScreenUtils.convertDpToPixels(getApplicationContext(), 3f));

            darkGrayFillPaint = new Paint();
            darkGrayFillPaint.setAntiAlias(true);
            darkGrayFillPaint.setColor(getResources().getColor(R.color.dark_gray));
            darkGrayFillPaint.setStyle(Paint.Style.FILL);

            whiteFillPaint = new Paint();
            whiteFillPaint.setAntiAlias(true);
            whiteFillPaint.setColor(Color.WHITE);
            whiteFillPaint.setStyle(Paint.Style.FILL);

            blackFillPaint = new Paint();
            blackFillPaint.setAntiAlias(true);
            blackFillPaint.setColor(Color.BLACK);
            blackFillPaint.setStyle(Paint.Style.FILL);

            circleStrokePaint = new Paint();
            circleStrokePaint.setColor(Color.WHITE);
            circleStrokePaint.setStyle(Paint.Style.STROKE);
            circleStrokePaint.setAntiAlias(true);
            circleStrokePaint.setStrokeWidth(ScreenUtils.convertDpToPixels(getApplicationContext(), 2f));

            complicationsCircleStrokePaint = new Paint();
            complicationsCircleStrokePaint.setColor(Color.WHITE);
            complicationsCircleStrokePaint.setStyle(Paint.Style.STROKE);
            complicationsCircleStrokePaint.setAntiAlias(true);
            complicationsCircleStrokePaint.setStrokeWidth(ScreenUtils.convertDpToPixels(getApplicationContext(), 1.0f));
            /*
            mHandPaint = new Paint();
            mHandPaint.setColor(resources.getColor(R.color.time_date_color));
            mHandPaint.setAntiAlias(true);
            mHandPaint.setStrokeCap(Paint.Cap.BUTT);
            mHandPaint.setTypeface(Typeface.createFromAsset(getApplicationContext().getAssets(), "fonts/Dolce Vita Light.ttf"));
            mHandPaint.setTextSize(getResources().getDimension(R.dimen.font_size_time_round));
*/

            mDatePaint = new Paint();
            mDatePaint.setColor(resources.getColor(R.color.time_date_color));
            mDatePaint.setAntiAlias(true);
            mDatePaint.setStrokeCap(Paint.Cap.BUTT);
            mDatePaint.setTypeface(NORMAL_TYPEFACE);
            mDatePaint.setTextSize(getResources().getDimension(R.dimen.font_size_date));
            mDatePaint.setTypeface(Typeface.createFromAsset(getApplicationContext().getAssets(), "fonts/Dolce Vita Light.ttf"));

            mTime = new Time();
            mCalendar = Calendar.getInstance();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    //mHandPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            //interactivity -> change dark or light theme
            if(INFO_DETAILS_MODE == 0) {
                darkGrayFillPaint.setColor(getResources().getColor(R.color.dark_gray));
                whiteFillPaint.setColor(Color.WHITE);
            }else {
                darkGrayFillPaint.setColor(Color.WHITE);
                whiteFillPaint.setColor(getResources().getColor(R.color.dark_gray));
            }

            int width = bounds.width();
            int height = bounds.height();
            /*
             * These calculations reflect the rotation in degrees per unit of time, e.g.,
             * 360 / 60 = 6 and 360 / 12 = 30.
             */
            final float seconds =
                    (mCalendar.get(Calendar.SECOND) + mCalendar.get(Calendar.MILLISECOND) / 1000f);
            final float secondsRotation = seconds * 6f;

            final float minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f;

            final float hourHandOffset = mCalendar.get(Calendar.MINUTE) / 2f;
            final float hoursRotation = (mCalendar.get(Calendar.HOUR) * 30) + hourHandOffset;

            // Draw the background.
            Resources resources = ShinyGradientsFace.this.getResources();

            int[] rainbow = GradientsUtils.getGradients(getApplicationContext(),selectedGradient);

            shader = new LinearGradient(0, 0, 0, bounds.width(), rainbow,
                    null, Shader.TileMode.MIRROR);

            Matrix matrix = new Matrix();
            matrix.setRotate(180);
            shader.setLocalMatrix(matrix);

            mBackgroundPaint.setShader(shader);

            Shader shaderHours = new LinearGradient(0, 0, 0, bounds.width(), rainbow,
                    null, Shader.TileMode.MIRROR);

            Matrix matrixH = new Matrix();
            matrixH.setRotate(-hoursRotation,width/2, height/2);
            shaderHours.setLocalMatrix(matrixH);

            if (!mAmbient) {
                //BACKGROUND WITH GRADIENT
                canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), mBackgroundPaint);
                canvas.drawCircle(width/2, height/2, width/4, darkGrayFillPaint);

                //seconds
                if(SECONDS_MODE == 0) {
                    canvas.save();
                    canvas.rotate(secondsRotation, width / 2, width / 2);
                    canvas.drawCircle(width / 2, width / 8, (width / 4f) / 6f, darkGrayFillPaint);
                    canvas.restore();
                }
            }else {//AMBIENT MODE
                //BLACK BG TO SAVE ENERGY
                canvas.drawColor(Color.BLACK);
            }

            //Hours and minutes hands
            canvas.save();
            canvas.rotate(minutesRotation, width / 2, width / 2);
            //canvas.drawLine(width / 2, height / 2 + (height / 15) * 1.5f, width / 2, height / 25 + height / 15 + height / 50, mSecondsCirclePaint);
            canvas.drawCircle(width / 2, height / 2, (width / 4f) / 8f, whiteFillPaint);
            Path minutesPath = new Path();
            minutesPath.moveTo( (width / 2) - ((width / 4f) / 8f), height / 2);
            minutesPath.lineTo((width / 2) + ((width / 4f) / 8f), height / 2);
            minutesPath.lineTo(width / 2, (height / 4)*1.1f);
            minutesPath.lineTo((width / 2) - ((width / 4f) / 8f), height / 2);
            minutesPath.close();
            canvas.drawPath(minutesPath, whiteFillPaint);
            canvas.restore();

            canvas.save();
            canvas.rotate(hoursRotation, width / 2, width / 2);
            if (!mAmbient) {
                mBackgroundPaint.setShader(shaderHours);
                canvas.drawCircle(width / 2, height / 2, (width / 4f) / 7f, mBackgroundPaint);
            }else {
                canvas.drawCircle(width / 2, height / 2, (width / 4f) / 7f, whiteFillPaint);
            }
            Path hoursPath = new Path();
            hoursPath.moveTo((width / 2) - ((width / 4f) / 7f), height / 2);
            hoursPath.lineTo((width / 2) + ((width / 4f) / 7f), height / 2);
            hoursPath.lineTo(width / 2, (height / 4)*1.3f);
            hoursPath.lineTo((width / 2) - ((width / 4f) / 7f), height / 2);
            hoursPath.close();
            if (!mAmbient) {
                canvas.drawPath(hoursPath, mBackgroundPaint);
            }else {
                canvas.drawPath(hoursPath, whiteFillPaint);
            }
            canvas.restore();



            int R = 3;
            if (!mAmbient) {
                canvas.drawCircle(width / 2, height / 2, ScreenUtils.convertDpToPixels(getApplicationContext(), R), darkGrayFillPaint);
            }else {
                canvas.drawCircle(width / 2, height / 2, ScreenUtils.convertDpToPixels(getApplicationContext(), R), blackFillPaint);
            }

            //COMPLICATIONS
            /*
            //MODE DAY WEEK AND DATE
            if(INFO_DETAILS_MODE == 0) {
                //WEEK DAY AND DAY OF MONTH
                String weekDay = new SimpleDateFormat("EEEE d").format(Calendar.getInstance().getTime()).toUpperCase();

                Rect dateBounds = new Rect();
                String format = weekDay.toUpperCase();
                mDatePaint.getTextBounds(format, 0, format.length(), dateBounds);

                int dateLeft = (int) ((double) width / (double) 2 - (double) dateBounds.width() / (double) 2);
                canvas.drawText(format, dateLeft, top + A_HEIGHT * 2, mDatePaint);
            } else {
                //int weekOfYear = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR);
                //DATE TEXT
                Rect dateBounds = new Rect();
                Locale current = getResources().getConfiguration().locale;
                DateFormat formatter = DateFormat.getDateInstance(DateFormat.LONG, current);
                //String pattern       = ((SimpleDateFormat)formatter).toPattern();
                String localPattern = ((SimpleDateFormat) formatter).toLocalizedPattern();
                String format = new SimpleDateFormat(localPattern).format(Calendar.getInstance().getTime()).toUpperCase();
                mDatePaint.getTextBounds(format, 0, format.length(), dateBounds);

                int dateLeft = (int) ((double) width / (double) 2 - (double) dateBounds.width() / (double) 2);
                canvas.drawText(format, dateLeft, top + A_HEIGHT * 2, mDatePaint);
            }
            */
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                mGoogleApiClient.connect();
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            ShinyGradientsFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            ShinyGradientsFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }


        private void updateConfigDataItemAndUiOnStartup() {
            NatureGradientsWatchFaceUtil.fetchConfigDataMap(mGoogleApiClient,
                    new NatureGradientsWatchFaceUtil.FetchConfigDataMapCallback() {
                        @Override
                        public void onConfigDataMapFetched(DataMap startupConfig) {
                            // If the DataItem hasn't been created yet or some keys are missing,
                            // use the default values.
                            setDefaultValuesForMissingConfigKeys(startupConfig);
                            NatureGradientsWatchFaceUtil.putConfigDataItem(mGoogleApiClient, startupConfig);

                            updateUiForConfigDataMap(startupConfig);
                        }
                    }
            );
        }

        private void setDefaultValuesForMissingConfigKeys(DataMap config) {
            addIntKeyIfMissing(config, NatureGradientsWatchFaceUtil.KEY_BACKGROUND_COLOR,
                    NatureGradientsWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_BACKGROUND);
        }

        private void addIntKeyIfMissing(DataMap config, String key, int color) {
            if (!config.containsKey(key)) {
                config.putInt(key, color);
            }
        }

        @Override // DataApi.DataListener
        public void onDataChanged(DataEventBuffer dataEvents) {
            for (DataEvent dataEvent : dataEvents) {
                if (dataEvent.getType() != DataEvent.TYPE_CHANGED) {
                    continue;
                }

                DataItem dataItem = dataEvent.getDataItem();
                if (!dataItem.getUri().getPath().equals(
                        NatureGradientsWatchFaceUtil.PATH_WITH_FEATURE)) {
                    continue;
                }

                DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
                DataMap config = dataMapItem.getDataMap();
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Config DataItem updated:" + config);
                }
                updateUiForConfigDataMap(config);
            }
        }

        private void updateUiForConfigDataMap(final DataMap config) {
            boolean uiUpdated = false;
            for (String configKey : config.keySet()) {
                if (!config.containsKey(configKey)) {
                    continue;
                }
                int color = config.getInt(configKey);
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Found watch face config key: " + configKey + " -> "
                            + color);
                }
                if (updateUiForKey(configKey, color)) {
                    uiUpdated = true;
                }
            }
            if (uiUpdated) {
                invalidate();
            }
        }

        /**
         * Updates the color of a UI item according to the given {@code configKey}. Does nothing if
         * {@code configKey} isn't recognized.
         *
         * @return whether UI has been updated
         */
        private boolean updateUiForKey(String configKey, int color) {
            if (configKey.equals(NatureGradientsWatchFaceUtil.KEY_BACKGROUND_COLOR)) {
                setGradient(color);
            } else {
                Log.w(TAG, "Ignoring unknown config key: " + configKey);
                return false;
            }
            return true;
        }

        private void setGradient(int color) {
            Log.d("color=",color+"");
            shader = null;
            selectedGradient = color;
        }

        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnected(Bundle connectionHint) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnected: " + connectionHint);
            }
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
            updateConfigDataItemAndUiOnStartup();
        }

        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnectionSuspended(int cause) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnectionSuspended: " + cause);
            }
        }

        @Override  // GoogleApiClient.OnConnectionFailedListener
        public void onConnectionFailed(ConnectionResult result) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnectionFailed: " + result);
            }
        }
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<ShinyGradientsFace.Engine> mWeakReference;

        public EngineHandler(ShinyGradientsFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            ShinyGradientsFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

}
