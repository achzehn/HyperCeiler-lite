package com.sevtinge.hyperceiler.about.widget;

import static androidx.core.content.ContextCompat.getSystemService;

import static com.sevtinge.hyperceiler.utils.PersistConfig.isAprilFoolsThemeView;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.sevtinge.hyperceiler.BuildConfig;
import com.sevtinge.hyperceiler.R;
import com.sevtinge.hyperceiler.about.controller.AboutAnimationController;
import com.sevtinge.hyperceiler.common.log.AndroidLog;
import com.sevtinge.hyperceiler.common.view.CubicEaseOutInterpolater;
import com.sevtinge.hyperceiler.libhook.utils.api.DisplayUtils;
import com.sevtinge.hyperceiler.utils.EggHelper;
import com.sevtinge.hyperceiler.utils.NotificationHelper;
import com.sevtinge.hyperceiler.utils.PermissionUtils;
import com.sevtinge.hyperceiler.utils.PersistConfig;
import com.sevtinge.hyperceiler.utils.SettingsFeatures;

import java.security.SecureRandom;
import java.util.Random;

import fan.cardview.HyperCardView;
import fan.core.utils.MiuiBlurUtils;
import fan.device.DeviceUtils;
import fan.internal.utils.ViewUtils;


public class VersionCard extends FrameLayout {
    private View mActionBar;
    private View mCardClickView;
    private AnimatorSet mAnimatorSet;
    private View mBgEffectView;
    private DecelerateInterpolator mDecelerateInterpolator;

    private View mIconView;
    private ImageView mIconImageView;
    private ImageView mTextIconImageView;

    private CubicEaseOutInterpolater mInterpolater;
    private boolean mNeedStartAnim = true;
    private boolean mNeedUpdate = false;
    ViewGroup mRootView;
    private HyperCardView mUpdateText;
    private ViewGroup mVersionLayout;
    private TextView mVersionNameText;
    private int mModeValue = 0;
    public AboutAnimationController mAboutAnimationController;
    private int mScrollValue = 0;
    private String mVersionName;
    private String mUpdateInfo = "";

    public VersionCard(@NonNull Context context) {
        super(context);
        initView();
    }

    public VersionCard(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    private void initView() {
        mRootView = (ViewGroup) LayoutInflater.from(getContext()).inflate(R.layout.app_version_card, this, true);

        mIconView = findViewById(R.id.app_logo);
        mIconImageView = findViewById(R.id.app_logo_view);
        mTextIconImageView = findViewById(R.id.app_text_logo_view);
        mVersionNameText = findViewById(R.id.app_version_text);
        mVersionLayout = findViewById(R.id.version_layout);

        refreshVersionName();

        mUpdateText = findViewById(R.id.update_hint_text);
        mUpdateText.setVisibility(View.GONE);

        mAnimatorSet = new AnimatorSet();
        mInterpolater = new CubicEaseOutInterpolater();
        mDecelerateInterpolator = new DecelerateInterpolator();

        mAboutAnimationController = new AboutAnimationController(getContext(), mNeedUpdate);

        if (mScrollValue == 0) {
            mIconView.setAlpha(1.0f);
            mVersionLayout.setAlpha(1.0f);
        } else {
            if (mActionBar != null && mBgEffectView != null) {
                mAboutAnimationController.startAnimation(mScrollValue, mIconView, mUpdateText, mVersionLayout, mActionBar, mBgEffectView);
            }
        }
        setEgg();
        setLogoBlur();
    }

    private void showEmojiBurst(View anchor, String emoji) {
        Context context = anchor.getContext();
        if (!(context instanceof Activity)) {
            return;
        }

        Activity activity = (Activity) context;
        FrameLayout decorView = (FrameLayout) activity.getWindow().getDecorView();

        TextView tv = new TextView(context);
        tv.setText(emoji);
        tv.setTextSize(24);

        int unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        tv.measure(unspecified, unspecified);
        int emojiWidth = tv.getMeasuredWidth();
        int emojiHeight = tv.getMeasuredHeight();

        int[] anchorLoc = new int[2];
        int[] decorLoc = new int[2];
        anchor.getLocationOnScreen(anchorLoc);
        decorView.getLocationOnScreen(decorLoc);

        float startX = anchorLoc[0] - decorLoc[0] + anchor.getWidth() / 2f - emojiWidth / 2f;
        float startY = anchorLoc[1] - decorLoc[1] + anchor.getHeight() / 2f - emojiHeight / 2f;

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.leftMargin = Math.round(startX);
        lp.topMargin = Math.round(startY);

        tv.setAlpha(0f);
        tv.setX(startX);
        tv.setY(startY);

        decorView.addView(tv, lp);

        Random random = new Random();

        long duration = 900 + random.nextInt(500);

        float vx = dp(context, 80) + random.nextFloat() * dp(context, 180);
        if (random.nextBoolean()) {
            vx = -vx;
        }

        float vy = -(dp(context, 280) + random.nextFloat() * dp(context, 220));
        float gravity = dp(context, 700) + random.nextFloat() * dp(context, 500);

        float rotateStart = -30f + random.nextFloat() * 60f;
        float rotateSpeed = -120f + random.nextFloat() * 240f;

        float startScale = 0.8f + random.nextFloat() * 0.4f;
        float peakScale = startScale + 0.2f + random.nextFloat() * 0.3f;

        tv.setScaleX(startScale);
        tv.setScaleY(startScale);
        tv.setRotation(rotateStart);

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(duration);
        animator.setInterpolator(new LinearInterpolator());

        float finalVx = vx;
        animator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            float t = fraction * (duration / 1000f);

            float x = startX + finalVx * t;
            float y = startY + vy * t + 0.5f * gravity * t * t;

            tv.setX(x);
            tv.setY(y);

            tv.setRotation(rotateStart + rotateSpeed * t);

            float scale;
            if (fraction < 0.35f) {
                float p = fraction / 0.35f;
                scale = lerp(startScale, peakScale, p);
            } else {
                float p = (fraction - 0.35f) / 0.65f;
                scale = lerp(peakScale, peakScale * 0.85f, p);
            }
            tv.setScaleX(scale);
            tv.setScaleY(scale);

            float alpha;
            if (fraction < 0.05f) {
                alpha = fraction / 0.05f;
            } else if (fraction < 0.55f) {
                alpha = 1f;
            } else {
                float p = (fraction - 0.55f) / 0.45f;
                alpha = 1f - p;
            }
            tv.setAlpha(Math.max(0f, alpha));
        });

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                decorView.removeView(tv);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                decorView.removeView(tv);
            }
        });

        tv.post(animator::start);
    }

    private float dp(Context context, float value) {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            context.getResources().getDisplayMetrics()
        );
    }

    private float lerp(float start, float end, float fraction) {
        return start + (end - start) * fraction;
    }

    private String randomEmoji() {
        String[] emojis = {"😀","😁","😂","\uD83E\uDEE0","😆","🤪","🤔","🥳","🫪","\uD83E\uDD2F","😎","\uD83E\uDD75","🫪","🔥","👽","👾","🤖","🤡","🐟","🍥","💥","❤\uFE0F","\uD83D\uDCAF","\uD83D\uDC37","\uD83D\uDC16","\uD83D\uDC3D","\uD83D\uDC3E","\uD83E\uDD8A","\uD83E\uDD84","\uD83D\uDC33","\uD83E\uDD90","\uD83C\uDF39","\uD83C\uDF49","♨\uFE0F","⭐","\uD83C\uDF1F","❄\uFE0F","✨","\uD83E\uDDE8","\uD83E\uDD47","\uD83C\uDFB2","\uD83C\uDFB1","\uD83C\uDFB5","\uD83C\uDFB6","\uD83D\uDCBE","\uD83D\uDCBF","\uD83D\uDDA5\uFE0F","\uD83D\uDCA1","\uD83D\uDCB5","\uD83D\uDCB0","\uD83E\uDE99","\uD83D\uDCA3","\uD83D\uDC8A","♿","\uD83D\uDD1D","❓","❗","⁉\uFE0F","\uD83C\uDE50","\uD83C\uDE51","\uD83C\uDFF3\uFE0F\u200D\uD83C\uDF08"};
        return emojis[(int) (Math.random() * emojis.length)];
    }

    private void setEgg() {
        mIconImageView.setOnClickListener(v -> {
            if (isAprilFoolsThemeView) {
                showEmojiBurst((ImageView) v, randomEmoji());
            } else {
                if (Math.random() >= 0.97) {
                    String[] messages = getResources().getStringArray(R.array.logo_click_egg_messages);
                    int index = new SecureRandom().nextInt(messages.length);
                    String msg = messages[index];
                    Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                }
            }
        });
        mIconImageView.setOnLongClickListener(v -> {
            if (Math.random() >= 0.6) {
                String[] messages = getResources().getStringArray(R.array.logo_click_egg_messages);
                int index = new SecureRandom().nextInt(messages.length);
                String msg = messages[index];

                if (!PermissionUtils.hasPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS)) {
                    Toast.makeText(getContext(), getResources().getString(R.string.logo_egg_Notification_tips), Toast.LENGTH_SHORT).show();
                    return true;
                } else {
                    NotificationHelper.createEggNotificationChannel(getContext());
                    // 发送通知
                    NotificationManager notificationManager = getSystemService(getContext(), NotificationManager.class);
                    int notificationId = "logo_channel_id".hashCode();
                    NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext(), "logo_channel_id")
                        .setSmallIcon(R.drawable.ic_hyperceiler)
                        .setContentTitle(getResources().getString(R.string.logo_egg_NotificationName))
                        .setContentText(msg)
                        .setAutoCancel(true);
                    builder.addExtras(EggHelper.INSTANCE.focusBuild(msg, getContext()));

                    notificationManager.notify(notificationId, builder.build());

                    // 9 秒后自动关闭
                    new Handler(Looper.getMainLooper()).postDelayed(() -> notificationManager.cancel(notificationId), 9000);


                    return true;
                }

            }
            return true;
        });
    }

    public AboutAnimationController getAboutAnimationController() {
        return mAboutAnimationController;
    }



    public void refreshBetaView(String str) {
        TextView textView = findViewById(R.id.version_text);
        if (textView != null) {
            String miuiBetaVersionInCard = "";
            if (!TextUtils.isEmpty(miuiBetaVersionInCard)) {
                textView.setText(miuiBetaVersionInCard);
            }
        }
    }

    public void refreshVersionName() {
        mVersionName = BuildConfig.VERSION_NAME + " | " + BuildConfig.BUILD_TYPE;
        if (!TextUtils.isEmpty(mVersionName) && mVersionNameText != null) {
            mVersionNameText.setText(mVersionName);
        }
    }



    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        performLogoAnimation(true);
    }

    public void performLogoAnimation(boolean z) {
    }

    private void setLogoBlur() {
        int logoView = isAprilFoolsThemeView ? R.drawable.ic_hyperceiler_logo_sp : R.drawable.ic_hyperceiler_logo;
        int textView = isAprilFoolsThemeView ? R.drawable.ic_text_logo_sp : R.drawable.ic_text_logo;
        int textViewLite = isAprilFoolsThemeView ? R.drawable.ic_text_logo_lite_sp : R.drawable.ic_text_logo_lite;
        if (!DeviceUtils.isMiuiLiteRom() && MiuiBlurUtils.isEnable() && MiuiBlurUtils.isEffectEnable(getContext())) {
            MiuiBlurUtils.setBackgroundBlur(mRootView, (int) ((getResources().getDisplayMetrics().density * 50.0f) + 0.5f));
            MiuiBlurUtils.setViewBlurMode(mRootView, 0);
            int[] logoColors = {
                getResources().getColor(R.color.app_about_logo_color1, getContext().getTheme()),
                getResources().getColor(R.color.app_about_logo_color2, getContext().getTheme()),
                getResources().getColor(R.color.app_about_logo_color3, getContext().getTheme())
            };
            if (ViewUtils.isNightMode(getContext().getApplicationContext())) {
                mModeValue = 18;
            } else {
                mModeValue = 19;
            }
            enableTextBlur(mIconImageView, true, logoColors, new int[]{mModeValue, 100, 106});
            mIconImageView.setBackgroundResource(logoView);

            enableTextBlur(mTextIconImageView, true, logoColors, new int[]{mModeValue, 100, 106});
            mTextIconImageView.setBackgroundResource(textView);

            AndroidLog.d("VersionCard", "startLogoBlur: enabled");
        } else {
            mIconImageView.setBackgroundResource(logoView);
            mTextIconImageView.setBackgroundResource(textViewLite);
        }
    }

    private void enableTextBlur(View view, boolean z, int[] iArr, int[] iArr2) {
        if (z) {
            MiuiBlurUtils.setViewBlurMode(view, 3);
            for (int i = 0; i < iArr.length; i++) {
                MiuiBlurUtils.addBackgroundBlenderColor(view, iArr[i], iArr2[i]);
            }
            return;
        }
        MiuiBlurUtils.setViewBlurMode(view, 0);
        MiuiBlurUtils.clearBackgroundBlendConfig(view);
    }

    public void setCardClickView(View view, View view2) {
        mActionBar = view2;
        mCardClickView = view;
        view2.setAccessibilityTraversalBefore(R.id.version_card_click_view);
        mCardClickView.setContentDescription(mVersionName);
    }

    public void stopLogoAnimation() {
        if (mAnimatorSet != null) {
            mAnimatorSet.end();
        }
    }

    public void setScrollValue(int value) {
        mScrollValue = value;
    }

    public void setAnimation(int i, View view, View view2) {
        mAboutAnimationController.startAnimation(i, mIconView, mUpdateText, mVersionLayout, view, view2);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }
}
