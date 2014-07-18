/*
 * Copyright (C) 2014 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.cyanogenmod.theme.chooserv2;

import android.animation.IntEvaluator;
import android.animation.ValueAnimator;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ThemeUtils;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.ThemeConfig;
import android.content.res.ThemeManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.provider.ThemesContract;
import android.provider.ThemesContract.PreviewColumns;
import android.provider.ThemesContract.ThemesColumns;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.cyanogenmod.theme.chooser.R;
import org.cyanogenmod.theme.chooserv2.ComponentSelector.OnItemClickedListener;
import org.cyanogenmod.theme.util.IconPreviewHelper;
import org.cyanogenmod.theme.util.ThemedTypefaceHelper;
import org.cyanogenmod.theme.util.TypefaceHelperCache;
import org.cyanogenmod.theme.util.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.provider.ThemesContract.ThemesColumns.MODIFIES_LAUNCHER;
import static android.provider.ThemesContract.ThemesColumns.MODIFIES_STATUS_BAR;
import static android.provider.ThemesContract.ThemesColumns.MODIFIES_NAVIGATION_BAR;
import static android.provider.ThemesContract.ThemesColumns.MODIFIES_ICONS;
import static android.provider.ThemesContract.ThemesColumns.MODIFIES_FONTS;

public class ThemeFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor>,
        ThemeManager.ThemeChangeListener {
    public static final int ANIMATE_START_DELAY = 200;
    public static final int ANIMATE_DURATION = 300;
    public static final int ANIMATE_INTERPOLATE_FACTOR = 3;
    public static final int ANIMATE_COMPONENT_CHANGE_DURATION = 200;
    public static final int ANIMATE_COMPONENT_ICON_DELAY = 50;
    public static final int ANIMATE_PROGRESS_IN_DURATION = 500;
    public static final int ANIMATE_TITLE_OUT_DURATION = 400;
    public static final int ANIMATE_PROGRESS_OUT_DURATION = 400;
    public static final int ANIMATE_TITLE_IN_DURATION = 500;

    private static final String NAVIGATION_BAR_BACKGROUND = "navbar_background";

    public static final String CURRENTLY_APPLIED_THEME = "currently_applied_theme";

    private static final ComponentName COMPONENT_DIALER =
            new ComponentName("com.android.dialer", "com.android.dialer.DialtactsActivity");
    private static final ComponentName COMPONENT_MESSAGING =
            new ComponentName("com.android.mms", "com.android.mms.ui.ConversationList");
    private static final ComponentName COMPONENT_CAMERANEXT =
            new ComponentName("com.cyngn.cameranext", "com.android.camera.CameraLauncher");
    private static final ComponentName COMPONENT_CAMERA =
            new ComponentName("com.android.camera2", "com.android.camera.CameraActivity");
    private static final ComponentName COMPONENT_BROWSER =
            new ComponentName("com.android.browser", "com.android.browser.BrowserActivity");
    private static final ComponentName COMPONENT_SETTINGS =
            new ComponentName("com.android.settings", "com.android.settings.Settings");
    private static final ComponentName COMPONENT_CALENDAR =
            new ComponentName("com.android.calendar", "com.android.calendar.AllInOneActivity");
    private static final ComponentName COMPONENT_GALERY =
            new ComponentName("com.android.gallery3d", "com.android.gallery3d.app.GalleryActivity");
    private static final String CAMERA_NEXT_PACKAGE = "com.cyngn.cameranext";

    private static final int LOADER_ID_ALL = 0;
    private static final int LOADER_ID_STATUS_BAR = 1;
    private static final int LOADER_ID_FONT = 2;
    private static final int LOADER_ID_ICONS = 3;
    private static final int LOADER_ID_WALLPAPER = 4;
    private static final int LOADER_ID_NAVIGATION_BAR = 5;

    private static ComponentName[] sIconComponents;

    /**
     * Maps the card's resource ID to a theme component
     */
    private static final SparseArray<String> sCardIdsToComponentTypes = new SparseArray<String>();

    private String mPkgName;
    private Typeface mTypefaceNormal;
    private int mBatteryStyle;

    private ViewGroup mScrollView;
    private ViewGroup mScrollContent;
    private ViewGroup mPreviewContent; // Contains icons, font, nav/status etc. Not wallpaper

    //Status Bar Views
    private ImageView mBluetooth;
    private ImageView mWifi;
    private ImageView mSignal;
    private ImageView mBattery;
    private TextView mClock;

    // Other Misc Preview Views
    private FrameLayout mShadowFrame;
    private ImageView mWallpaper;
    private ViewGroup mStatusBar;
    private TextView mFontPreview;
    private ViewGroup mIconContainer;

    // Nav Bar Views
    private ViewGroup mNavBar;
    private ImageView mBackButton;
    private ImageView mHomeButton;
    private ImageView mRecentButton;

    // Title Card Views
    private ViewGroup mTitleCard;
    private ViewGroup mTitleLayout;
    private TextView mTitle;
    private ImageView mApply;
    private ImageView mOverflow;
    private ProgressBar mProgress;

    // Additional Card Views
    private RelativeLayout mAdditionalCards;
    private WallpaperCardView mWallpaperCard;

    private Handler mHandler;

    private int mActiveCardId = -1;
    private ComponentSelector mSelector;
    private Map<String, String> mSelectedComponentsMap;

    static {
        sCardIdsToComponentTypes.put(R.id.status_bar_container, MODIFIES_STATUS_BAR);
        sCardIdsToComponentTypes.put(R.id.font_preview_container, MODIFIES_FONTS);
        sCardIdsToComponentTypes.put(R.id.icon_container, MODIFIES_ICONS);
        sCardIdsToComponentTypes.put(R.id.navigation_bar_container, MODIFIES_NAVIGATION_BAR);
        sCardIdsToComponentTypes.put(R.id.wallpaper_card, MODIFIES_LAUNCHER);
    }

    static ThemeFragment newInstance(String pkgName) {
        ThemeFragment f = new ThemeFragment();
        Bundle args = new Bundle();
        args.putString("pkgName", pkgName);
        f.setArguments(args);
        return f;
    }

    /**
     * When creating, retrieve this instance's number from its arguments.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPkgName = getArguments().getString("pkgName");
        mBatteryStyle = Settings.System.getInt(getActivity().getContentResolver(),
                Settings.System.STATUS_BAR_BATTERY, 0);

        getIconComponents(getActivity());
        ThemedTypefaceHelper helper = new ThemedTypefaceHelper();
        helper.load(getActivity(), CURRENTLY_APPLIED_THEME.equals(mPkgName) ?
                getAppliedFontPackageName() : mPkgName);
        mTypefaceNormal = helper.getTypeface(Typeface.NORMAL);

        mHandler = new Handler();

        // populate mSelectedComponentsMap with supported components for this theme
        if (!CURRENTLY_APPLIED_THEME.equals(mPkgName)) {
            List<String> components = ThemeUtils.getSupportedComponents(getActivity(), mPkgName);
            mSelectedComponentsMap = new HashMap<String, String>(components.size());
            for (String component : components) {
                mSelectedComponentsMap.put(component, mPkgName);
            }
        } else {
            mSelectedComponentsMap = new HashMap<String, String>();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.v2_fragment_pager_list, container, false);

        mScrollView = (ViewGroup) v.findViewById(android.R.id.list);
        mScrollContent = (ViewGroup) mScrollView.getChildAt(0);
        mPreviewContent = (ViewGroup) v.findViewById(R.id.preview_container);

        // Status Bar
        mStatusBar = (ViewGroup) v.findViewById(R.id.status_bar);
        mBluetooth = (ImageView) v.findViewById(R.id.bluetooth_icon);
        mWifi = (ImageView) v.findViewById(R.id.wifi_icon);
        mSignal = (ImageView) v.findViewById(R.id.signal_icon);
        mBattery = (ImageView) v.findViewById(R.id.battery);
        mClock = (TextView) v.findViewById(R.id.clock);

        // Wallpaper / Font / Icons / etc
        mWallpaper = (ImageView) v.findViewById(R.id.wallpaper);
        mFontPreview = (TextView) v.findViewById(R.id.font_preview);
        mFontPreview.setTypeface(mTypefaceNormal);
        mIconContainer = (ViewGroup) v.findViewById(R.id.icon_container);
        mShadowFrame = (FrameLayout) v.findViewById(R.id.shadow_frame);

        // Nav Bar
        mNavBar = (ViewGroup) v.findViewById(R.id.navigation_bar);
        mBackButton = (ImageView) v.findViewById(R.id.back_button);
        mHomeButton = (ImageView) v.findViewById(R.id.home_button);
        mRecentButton = (ImageView) v.findViewById(R.id.recent_button);

        // Title Card
        mTitleCard = (ViewGroup)v.findViewById(R.id.title_card);
        mTitleLayout = (ViewGroup) v.findViewById(R.id.title_layout);
        mTitle = (TextView) v.findViewById(R.id.title);
        mProgress = (ProgressBar) v.findViewById(R.id.apply_progress);
        mOverflow = (ImageView) v.findViewById(R.id.overflow);
        mApply = (ImageView) v.findViewById(R.id.apply);
        mApply.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                applyTheme();
            }
        });

        // Additional cards which should hang out offscreen until expanded
        mAdditionalCards = (RelativeLayout) v.findViewById(R.id.additional_cards);
        mWallpaperCard = (WallpaperCardView) v.findViewById(R.id.wallpaper_card);
        int translationY = getDistanceToMoveBelowScreen(mWallpaperCard);
        mWallpaperCard.setTranslationY(translationY);


        getLoaderManager().initLoader(LOADER_ID_ALL, null, this);

        initCards(v);

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (CURRENTLY_APPLIED_THEME.equals(mPkgName)) {
            if (getLoaderManager().getLoader(0) != null) {
                getLoaderManager().restartLoader(0, null, this);
            }
        }
    }

    @Override
    public void onProgress(int progress) {
        mProgress.setProgress(progress);
    }

    @Override
    public void onFinish(boolean isSuccess) {
        // We post a runnable to mHandler so the client is removed from the same thread
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                ThemeManager tm = getThemeManager();
                if (tm != null) tm.removeClient(ThemeFragment.this);
            }
        });
        if (isSuccess) {
            mProgress.setProgress(100);
            animateProgressOut();
            ((ChooserActivity) getActivity()).themeChangeEnded();
        }
    }

    public void expand() {
        // Full width and height!
        ViewGroup content = (ViewGroup) mScrollView.getParent();
        content.setPadding(0, 0, 0, 0);
        ViewGroup.LayoutParams  layoutParams = mPreviewContent.getLayoutParams();
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        mPreviewContent.setLayoutParams(layoutParams);
        mScrollView.setPadding(0,0,0,0);

        // The parent of the wallpaper squishes the wp slightly because of padding from the 9 patch
        // When the parent expands, the wallpaper returns to regular size which creates an
        // undesireable effect.
        Rect padding = new Rect();
        NinePatchDrawable bg = (NinePatchDrawable) mShadowFrame.getBackground();
        bg.getPadding(padding);
        ViewGroup.LayoutParams wpParams = mWallpaper.getLayoutParams();
        wpParams.width -= padding.left + padding.right;
        mWallpaper.setLayoutParams(wpParams);
        mIconContainer.setPadding(padding.left, padding.top, padding.right, padding.bottom);
        mShadowFrame.setBackground(null);
        mAdditionalCards.setPadding(padding.left, padding.top, padding.right, padding.bottom);

        // Off screen cards will become visible and then be animated in
        mWallpaperCard.setVisibility(View.VISIBLE);

        // Expand the children
        int top = (int) getResources()
                .getDimension(R.dimen.expanded_card_margin_top);
        for (int i = 0; i < mPreviewContent.getChildCount(); i++) {
            ComponentCardView child = (ComponentCardView) mPreviewContent.getChildAt(i);

            RelativeLayout.LayoutParams lparams =
                    (RelativeLayout.LayoutParams) child.getLayoutParams();
            lparams.setMargins(0, top, 0, 0);
            if (child.getId() == R.id.navigation_bar_container) {
                lparams.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                lparams.addRule(RelativeLayout.BELOW, R.id.icon_container);
            }


            child.setLayoutParams(lparams);
            child.expand();
        }

        // Collect the present position of all the children. The next layout/draw cycle will
        // change these bounds since we just expanded them. Then we can animate from prev location
        // to the new location. Note that the order of these calls matter as they all
        // add themselves to the root layout as overlays
        mScrollView.requestLayout();
        animateWallpaperOut();
        animateTitleCard(true, false);
        animateChildren(true, getChildrensGlobalBounds());
        animateExtras(true, getChildrensGlobalBounds());
        mSelector = ((ChooserActivity) getActivity()).getComponentSelector();
        mSelector.setOnItemClickedListener(mOnComponentItemClicked);
    }



    // Returns the boundaries for all the children in the scrollview relative to the window
    private List<Rect> getChildrensGlobalBounds() {
        List<Rect> bounds = new ArrayList<Rect>();
        for (int i = 0; i < mPreviewContent.getChildCount(); i++) {
            final View v = mPreviewContent.getChildAt(i);
            int[] pos = new int[2];
            v.getLocationInWindow(pos);
            Rect boundary = new Rect(pos[0], pos[1], pos[0] + v.getWidth(), pos[1]+v.getHeight());
            bounds.add(boundary);
        }
        return bounds;
    }

    public void fadeOutCards(Runnable endAction) {
        for (int i = 0; i < mPreviewContent.getChildCount(); i++) {
            ComponentCardView v = (ComponentCardView) mPreviewContent.getChildAt(i);
            v.animateFadeOut();
        }
        mHandler.postDelayed(endAction, ComponentCardView.CARD_FADE_DURATION);
    }

    public void collapse(final boolean applyTheme) {
        // Pad the  view so it appears thinner
        ViewGroup content = (ViewGroup) mScrollView.getParent();
        Resources r = mScrollView.getContext().getResources();
        int leftRightPadding = (int) r.getDimension(R.dimen.collapsed_theme_page_padding);
        content.setPadding(leftRightPadding, 0, leftRightPadding, 0);

        //Move the theme preview so that it is near the center of page per spec
        int paddingTop = (int) r.getDimension(R.dimen.collapsed_theme_page_padding_top);
        mScrollView.setPadding(0, paddingTop, 0, 0);

        // During expand the wallpaper size decreases slightly to makeup for 9patch padding
        // so when we collapse we should increase it again.
        mShadowFrame.setBackgroundResource(R.drawable.bg_themepreview_shadow);
        Rect padding = new Rect();
        final NinePatchDrawable bg = (NinePatchDrawable) mShadowFrame.getBackground();
        bg.getPadding(padding);
        ViewGroup.LayoutParams wpParams = mWallpaper.getLayoutParams();
        wpParams.width += padding.left + padding.right;
        mWallpaper.setLayoutParams(wpParams);

        // Gradually fade the drop shadow back in or else it will be out of place
        ValueAnimator shadowAnimation = ValueAnimator.ofObject(new IntEvaluator(), 0, 255);
        shadowAnimation.setDuration(ANIMATE_DURATION);
        shadowAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                bg.setAlpha((Integer) animator.getAnimatedValue());
            }

        });
        shadowAnimation.start();

        //Move the title card back in
        mTitleCard.setVisibility(View.VISIBLE);
        mTitleCard.setTranslationY(0);

        // Shrink the height
        ViewGroup.LayoutParams layoutParams = mPreviewContent.getLayoutParams();
        Resources resources = mPreviewContent.getResources();
        layoutParams.height = (int) resources.getDimension(R.dimen.theme_preview_height);

        for (int i = 0; i < mPreviewContent.getChildCount(); i++) {
            ComponentCardView child = (ComponentCardView) mPreviewContent.getChildAt(i);
            RelativeLayout.LayoutParams lparams =
                    (RelativeLayout.LayoutParams) child.getLayoutParams();
            lparams.setMargins(0, 0, 0, 0);

            if (child.getId() == R.id.navigation_bar_container) {
                lparams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                lparams.removeRule(RelativeLayout.BELOW);
            } else if (child.getId() == R.id.icon_container) {
                int top = (int) child.getResources()
                        .getDimension(R.dimen.collapsed_icon_card_margin_top);
                lparams.setMargins(0, top, 0, 0);
            }

            child.getLayoutParams();
            child.collapse();
        }

        mScrollView.requestLayout();
        animateChildren(false, getChildrensGlobalBounds());
        animateExtras(false, getChildrensGlobalBounds());
        animateWallpaperIn();
        animateTitleCard(false, applyTheme);
    }

    // This will animate the children's vertical positions between the previous bounds and the
    // new bounds which occur on the next draw
    private void animateChildren(final boolean isExpanding, final List<Rect> prevBounds) {
        final ViewGroup root = (ViewGroup) getActivity().getWindow()
                .getDecorView().findViewById(android.R.id.content);

        // Grab the child's new location and animate from prev to current loc.
        final ViewTreeObserver observer = mScrollContent.getViewTreeObserver();
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            public boolean onPreDraw() {
                observer.removeOnPreDrawListener(this);

                for (int i = mPreviewContent.getChildCount() - 1; i >= 0; i--) {
                    final ComponentCardView v = (ComponentCardView) mPreviewContent.getChildAt(i);

                    float prevY;
                    float endY;
                    float prevHeight;
                    float endHeight;
                    if (i >= prevBounds.size()) {
                        // View is being created
                        prevY = mPreviewContent.getTop() + mPreviewContent.getHeight();
                        endY = v.getY();
                        prevHeight = v.getHeight();
                        endHeight = v.getHeight();
                    } else {
                        Rect boundary = prevBounds.get(i);
                        prevY = boundary.top;
                        prevHeight = boundary.height();

                        int[] endPos = new int[2];
                        v.getLocationInWindow(endPos);
                        endY = endPos[1];
                        endHeight = v.getHeight();
                    }

                    v.setTranslationY((prevY - endY) + (prevHeight - endHeight) / 2);
                    root.getOverlay().add(v);

                    // Expanding has a delay while the wallpaper begins to fade out
                    // Collapsing is opposite of this so wallpaper will have the delay instead
                    int startDelay = isExpanding ? ANIMATE_START_DELAY : 0;

                    v.animate()
                            .setStartDelay(startDelay)
                            .translationY(0)
                            .setDuration(ANIMATE_DURATION)
                            .setInterpolator(
                                    new DecelerateInterpolator(ANIMATE_INTERPOLATE_FACTOR))
                            .withEndAction(new Runnable() {
                                public void run() {
                                    root.getOverlay().remove(v);
                                    mPreviewContent.addView(v, 0);
                                }
                            });
                    v.postDelayed(new Runnable() {
                        public void run() {
                            if (isExpanding) {
                                v.animateExpand();
                            }
                        }
                    }, ANIMATE_DURATION / 2);
                }
                return true;
            }
        });
    }

    private void animateExtras(final boolean isExpanding, final List<Rect> prevBounds) {
        final ViewGroup root = (ViewGroup) getActivity().getWindow()
                .getDecorView().findViewById(android.R.id.content);
        // Expanding has a delay while the wallpaper begins to fade out
        // Collapsing is opposite of this so wallpaper will have the delay instead
        final int startDelay = isExpanding ? ANIMATE_START_DELAY : 0;

        if (!isExpanding) {
            // If we are collapsing then we do not wait for the onPreDraw() because
            // 1) We will simply translate the view out and 2) The view will get "squished"
            // during the layout change (due to parent's padding) which is undesireable.
            for (int i = mAdditionalCards.getChildCount() - 1; i >= 0; i--) {
                final ComponentCardView v = (ComponentCardView) mAdditionalCards.getChildAt(i);
                root.getOverlay().add(v);

                int translationY = getDistanceToMoveBelowScreen(v);
                v.animate()
                        .setStartDelay(0)
                        .translationY(translationY)
                        .setDuration(ANIMATE_DURATION)
                        .setInterpolator(
                                new DecelerateInterpolator(ANIMATE_INTERPOLATE_FACTOR))
                        .withEndAction(new Runnable() {
                            public void run() {
                                root.getOverlay().remove(v);
                                mAdditionalCards.addView(v, 0);
                                v.setVisibility(View.INVISIBLE);
                            }
                        });
            }
        } else {
            final ViewTreeObserver observer = mScrollContent.getViewTreeObserver();
            observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                public boolean onPreDraw() {
                    observer.removeOnPreDrawListener(this);

                    for (int i = mAdditionalCards.getChildCount() - 1; i >= 0; i--) {
                        final ComponentCardView v =
                                (ComponentCardView) mAdditionalCards.getChildAt(i);
                        root.getOverlay().add(v);
                        v.animate()
                                .setStartDelay(startDelay)
                                .translationY(0)
                                .setDuration(ANIMATE_DURATION+100)
                                .setInterpolator(
                                        new DecelerateInterpolator(ANIMATE_INTERPOLATE_FACTOR))
                                .withEndAction(new Runnable() {
                                    public void run() {
                                        root.getOverlay().remove(v);
                                        mAdditionalCards.addView(v, 0);
                                    }
                                });

                    }
                    return false;
                }
            });
        }
    }

    private int getDistanceToMoveBelowScreen(View v) {
        Display display = getActivity().getWindowManager().getDefaultDisplay();
        Point p = new Point();
        display.getSize(p);
        int heightId = getResources()
                .getIdentifier("system_bar_height", "dimen", "android");
        int navbar_height = getResources().getDimensionPixelSize(heightId);
        int[] pos = new int[2];
        v.getLocationInWindow(pos);
        return p.y + navbar_height - pos[1];
    }

    private void animateTitleCard(final boolean expand, final boolean applyTheme) {
        final ViewGroup parent = (ViewGroup) mTitleCard.getParent();
        // Get current location of the title card
        int[] location = new int[2];
        mTitleCard.getLocationOnScreen(location);
        final int prevY = location[1];

        final ViewTreeObserver observer = mScrollContent.getViewTreeObserver();
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            public boolean onPreDraw() {
                observer.removeOnPreDrawListener(this);

                final ViewGroup root = (ViewGroup) getActivity().getWindow()
                        .getDecorView().findViewById(android.R.id.content);

                root.getOverlay().add(mTitleCard);

                //Move title card back where it was before the relayout
                float alpha = 1f;
                if (expand) {
                    int[] endPos = new int[2];
                    mTitleCard.getLocationInWindow(endPos);
                    int endY = endPos[1];
                    mTitleCard.setTranslationY(prevY - endY);
                    alpha = 0;
                } else {
                }

                // Fade the title card and move it out of the way
                mTitleCard.animate()
                        .alpha(alpha)
                        .setDuration(ANIMATE_DURATION)
                        .withEndAction(new Runnable() {
                            public void run() {
                                root.getOverlay().remove(mTitleCard);
                                parent.addView(mTitleCard);
                                if (expand) {
                                    mTitleCard.setVisibility(View.GONE);
                                } else if (applyTheme) {
                                    // since the title card is the last animation when collapsing
                                    // we will handle applying the theme, if applicable, here
                                    applyTheme();
                                }
                            }
                        });
                return true;
            }
        });
    }

    private void animateWallpaperOut() {
        final ViewGroup root = (ViewGroup) getActivity().getWindow()
                .getDecorView().findViewById(android.R.id.content);

        int[] location = new int[2];
        mWallpaper.getLocationOnScreen(location);

        final int prevY = location[1];

        final ViewTreeObserver observer = mScrollContent.getViewTreeObserver();
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            public boolean onPreDraw() {
                observer.removeOnPreDrawListener(this);
                root.getOverlay().add(mWallpaper);

                int[] location = new int[2];
                mWallpaper.getLocationOnScreen(location);
                final int newY = location[1];

                mWallpaper.setTranslationY(prevY - newY);
                mWallpaper.animate()
                        .alpha(0f)
                        .setDuration(300)
                        .withEndAction(new Runnable() {
                            public void run() {
                                root.getOverlay().remove(mWallpaper);
                                mShadowFrame.addView(mWallpaper, 0);
                                mWallpaper.setVisibility(View.GONE);
                            }
                        });
                return true;
            }
        });
    }

    private void animateWallpaperIn() {
                mWallpaper.setVisibility(View.VISIBLE);
                mWallpaper.setTranslationY(0);
                mWallpaper.animate()
                        .alpha(1f)
                        .setDuration(300);
    }

    private String getAppliedFontPackageName() {
        final Configuration config = getActivity().getResources().getConfiguration();
        final ThemeConfig themeConfig = config != null ? config.themeConfig : null;
        return themeConfig != null ? themeConfig.getFontPkgName() :
                ThemeConfig.getSystemTheme().getFontPkgName();
    }

    private ThemeManager getThemeManager() {
        final Context context = getActivity();
        if (context != null) {
            return (ThemeManager) context.getSystemService(Context.THEME_SERVICE);
        }
        return null;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String pkgName = mPkgName;
        if (args != null) {
            pkgName = args.getString("pkgName");
        }
        Uri uri = ThemesContract.PreviewColumns.CONTENT_URI;
        String selection = ThemesContract.ThemesColumns.PKG_NAME + "= ?";
        String[] selectionArgs = new String[] { pkgName };
        String[] projection = null;
        switch (id) {
            case LOADER_ID_ALL:
                if (!CURRENTLY_APPLIED_THEME.equals(pkgName)) {
                    projection = new String[] {
                            ThemesColumns.PKG_NAME,
                            ThemesColumns.TITLE,
                            ThemesColumns.WALLPAPER_URI,
                            ThemesColumns.HOMESCREEN_URI,
                            PreviewColumns.WALLPAPER_PREVIEW,
                            PreviewColumns.STATUSBAR_BACKGROUND,
                            PreviewColumns.STATUSBAR_WIFI_ICON,
                            PreviewColumns.STATUSBAR_WIFI_COMBO_MARGIN_END,
                            PreviewColumns.STATUSBAR_BLUETOOTH_ICON,
                            PreviewColumns.STATUSBAR_SIGNAL_ICON,
                            PreviewColumns.STATUSBAR_CLOCK_TEXT_COLOR,
                            PreviewColumns.STATUSBAR_BATTERY_CIRCLE,
                            PreviewColumns.STATUSBAR_BATTERY_LANDSCAPE,
                            PreviewColumns.STATUSBAR_BATTERY_PORTRAIT,
                            PreviewColumns.NAVBAR_BACK_BUTTON,
                            PreviewColumns.NAVBAR_HOME_BUTTON,
                            PreviewColumns.NAVBAR_RECENT_BUTTON,
                            PreviewColumns.ICON_PREVIEW_1,
                            PreviewColumns.ICON_PREVIEW_2,
                            PreviewColumns.ICON_PREVIEW_3,
                            PreviewColumns.ICON_PREVIEW_4
                    };
                } else {
                    projection = new String[] {
                            PreviewColumns.WALLPAPER_PREVIEW,
                            PreviewColumns.STATUSBAR_BACKGROUND,
                            PreviewColumns.STATUSBAR_WIFI_ICON,
                            PreviewColumns.STATUSBAR_WIFI_COMBO_MARGIN_END,
                            PreviewColumns.STATUSBAR_BLUETOOTH_ICON,
                            PreviewColumns.STATUSBAR_SIGNAL_ICON,
                            PreviewColumns.STATUSBAR_CLOCK_TEXT_COLOR,
                            PreviewColumns.STATUSBAR_BATTERY_CIRCLE,
                            PreviewColumns.STATUSBAR_BATTERY_LANDSCAPE,
                            PreviewColumns.STATUSBAR_BATTERY_PORTRAIT,
                            PreviewColumns.NAVBAR_BACK_BUTTON,
                            PreviewColumns.NAVBAR_HOME_BUTTON,
                            PreviewColumns.NAVBAR_RECENT_BUTTON,
                            PreviewColumns.ICON_PREVIEW_1,
                            PreviewColumns.ICON_PREVIEW_2,
                            PreviewColumns.ICON_PREVIEW_3,
                            PreviewColumns.ICON_PREVIEW_4,
                            // TODO: add this to the ThemesContract if this
                            // design moves beyond prototype
                            NAVIGATION_BAR_BACKGROUND
                    };
                    uri = PreviewColumns.APPLIED_URI;
                    selection = null;
                    selectionArgs = null;
                }
                break;
            case LOADER_ID_STATUS_BAR:
                projection = new String[] {
                        ThemesColumns.PKG_NAME,
                        ThemesColumns.TITLE,
                        PreviewColumns.STATUSBAR_BACKGROUND,
                        PreviewColumns.STATUSBAR_WIFI_ICON,
                        PreviewColumns.STATUSBAR_WIFI_COMBO_MARGIN_END,
                        PreviewColumns.STATUSBAR_BLUETOOTH_ICON,
                        PreviewColumns.STATUSBAR_SIGNAL_ICON,
                        PreviewColumns.STATUSBAR_CLOCK_TEXT_COLOR,
                        PreviewColumns.STATUSBAR_BATTERY_CIRCLE,
                        PreviewColumns.STATUSBAR_BATTERY_LANDSCAPE,
                        PreviewColumns.STATUSBAR_BATTERY_PORTRAIT
                };
                break;
            case LOADER_ID_FONT:
                projection = new String[] {
                        ThemesColumns.PKG_NAME,
                        ThemesColumns.TITLE
                };
                break;
            case LOADER_ID_ICONS:
                projection = new String[] {
                        ThemesColumns.PKG_NAME,
                        ThemesColumns.TITLE,
                        PreviewColumns.ICON_PREVIEW_1,
                        PreviewColumns.ICON_PREVIEW_2,
                        PreviewColumns.ICON_PREVIEW_3,
                        PreviewColumns.ICON_PREVIEW_4
                };
                break;
            case LOADER_ID_WALLPAPER:
                projection = new String[] {
                        ThemesColumns.PKG_NAME,
                        ThemesColumns.TITLE,
                        PreviewColumns.WALLPAPER_PREVIEW
                };
                break;
            case LOADER_ID_NAVIGATION_BAR:
                projection = new String[] {
                        ThemesColumns.PKG_NAME,
                        ThemesColumns.TITLE,
                        PreviewColumns.STATUSBAR_BACKGROUND,
                        PreviewColumns.NAVBAR_BACK_BUTTON,
                        PreviewColumns.NAVBAR_HOME_BUTTON,
                        PreviewColumns.NAVBAR_RECENT_BUTTON
                };
                break;
        }
        return new CursorLoader(getActivity(), uri, projection, selection, selectionArgs, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor c) {
        c.moveToFirst();
        if (c.getCount() == 0) return;
        switch (loader.getId()) {
            case LOADER_ID_ALL:
                loadWallpaper(c);
                loadStatusBar(c, false);
                loadIcons(c, false);
                loadNavBar(c, false);
                loadTitle(c);
                loadFont(c, false);
                break;
            case LOADER_ID_STATUS_BAR:
                loadStatusBar(c, true);
                break;
            case LOADER_ID_FONT:
                loadFont(c, true);
                break;
            case LOADER_ID_ICONS:
                loadIcons(c, true);
                break;
            case LOADER_ID_WALLPAPER:
                loadWallpaper(c);
                break;
            case LOADER_ID_NAVIGATION_BAR:
                loadNavBar(c, true);
                break;
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {}

    private void loadTitle(Cursor c) {
        if (CURRENTLY_APPLIED_THEME.equals(mPkgName)) {
            mTitle.setText(R.string.my_theme);
        } else {
            int titleIdx = c.getColumnIndex(ThemesColumns.TITLE);
            String title = c.getString(titleIdx);
            mTitle.setText(title);
        }
    }

    private void loadWallpaper(Cursor c) {
        int pkgNameIdx = c.getColumnIndex(ThemesColumns.PKG_NAME);
        int wpIdx = c.getColumnIndex(PreviewColumns.WALLPAPER_PREVIEW);

        if (pkgNameIdx > -1) {
            Bitmap bitmap = Utils.loadBitmapBlob(c, wpIdx);
            mWallpaper.setImageBitmap(bitmap);
            mWallpaperCard.setWallpaper(new BitmapDrawable(bitmap));
            String pkgName = c.getString(pkgNameIdx);
            mSelectedComponentsMap.put(MODIFIES_LAUNCHER, pkgName);
        } else {
            Drawable wp = getActivity().getWallpaper();
            if (wp == null) {
                wp = new BitmapDrawable(Utils.loadBitmapBlob(c, wpIdx));
            }
            mWallpaper.setBackground(wp);
            mWallpaperCard.setWallpaper(wp);
        }
    }

    private void loadStatusBar(Cursor c, boolean animate) {
        int backgroundIdx = c.getColumnIndex(PreviewColumns.STATUSBAR_BACKGROUND);
        int wifiIdx = c.getColumnIndex(PreviewColumns.STATUSBAR_WIFI_ICON);
        int wifiMarginIdx = c.getColumnIndex(PreviewColumns.STATUSBAR_WIFI_COMBO_MARGIN_END);
        int bluetoothIdx = c.getColumnIndex(PreviewColumns.STATUSBAR_BLUETOOTH_ICON);
        int signalIdx = c.getColumnIndex(PreviewColumns.STATUSBAR_SIGNAL_ICON);
        int batteryIdx = c.getColumnIndex(Utils.getBatteryIndex(mBatteryStyle));
        int clockColorIdx = c.getColumnIndex(PreviewColumns.STATUSBAR_CLOCK_TEXT_COLOR);
        int pkgNameIdx = c.getColumnIndex(ThemesColumns.PKG_NAME);

        Bitmap background = Utils.loadBitmapBlob(c, backgroundIdx);
        Bitmap bluetoothIcon = Utils.loadBitmapBlob(c, bluetoothIdx);
        Bitmap wifiIcon = Utils.loadBitmapBlob(c, wifiIdx);
        Bitmap signalIcon = Utils.loadBitmapBlob(c, signalIdx);
        Bitmap batteryIcon = Utils.loadBitmapBlob(c, batteryIdx);
        int wifiMargin = c.getInt(wifiMarginIdx);
        int clockTextColor = c.getInt(clockColorIdx);

        if (!mStatusBar.isDrawingCacheEnabled()) mStatusBar.setDrawingCacheEnabled(true);
        Drawable d = null;
        if (animate) {
            d = new BitmapDrawable(getResources(), mStatusBar.getDrawingCache());
        }

        mStatusBar.setBackground(new BitmapDrawable(getActivity().getResources(), background));
        mBluetooth.setImageBitmap(bluetoothIcon);
        mWifi.setImageBitmap(wifiIcon);
        mSignal.setImageBitmap(signalIcon);
        mBattery.setImageBitmap(batteryIcon);
        mClock.setTextColor(clockTextColor);

        ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) mWifi.getLayoutParams();
        params.setMarginEnd(wifiMargin);
        mWifi.setLayoutParams(params);

        if (mBatteryStyle == 4) {
            mBattery.setVisibility(View.GONE);
        } else {
            mBattery.setVisibility(View.VISIBLE);
        }
        mStatusBar.post(new Runnable() {
            @Override
            public void run() {
                mStatusBar.invalidate();
            }
        });
        if (pkgNameIdx > -1) {
            String pkgName = c.getString(pkgNameIdx);
            mSelectedComponentsMap.put(MODIFIES_STATUS_BAR, pkgName);
        }
        if (animate) {
            animateContentChange(R.id.status_bar_container, mStatusBar, d);
        }
    }

    private void loadIcons(Cursor c, boolean animate) {
        int[] iconIdx = new int[4];
        iconIdx[0] = c.getColumnIndex(PreviewColumns.ICON_PREVIEW_1);
        iconIdx[1] = c.getColumnIndex(PreviewColumns.ICON_PREVIEW_2);
        iconIdx[2] = c.getColumnIndex(PreviewColumns.ICON_PREVIEW_3);
        iconIdx[3] = c.getColumnIndex(PreviewColumns.ICON_PREVIEW_4);
        int pkgNameIdx = c.getColumnIndex(ThemesColumns.PKG_NAME);

        // Set the icons. If the provider does not have an icon preview then
        // fall back to the default icon set
        IconPreviewHelper helper = new IconPreviewHelper(getActivity(), "");
        ViewGroup container = (ViewGroup) mIconContainer.findViewById(R.id.icon_preview_container);
        for(int i=0; i < container.getChildCount() && i < iconIdx.length; i++) {
            final ImageView v = (ImageView) ((ViewGroup)mIconContainer.getChildAt(1)).getChildAt(i);
            Bitmap bitmap = Utils.loadBitmapBlob(c, iconIdx[i]);
            Drawable oldIcon = v.getDrawable();
            Drawable newIcon;
            if (bitmap == null) {
                ComponentName component = sIconComponents[i];
                newIcon = helper.getDefaultIcon(component.getPackageName(),
                        component.getClassName());
            } else {
                newIcon = new BitmapDrawable(getResources(), bitmap);
            }
            if (animate) {
                Drawable[] layers = new Drawable[2];
                layers[0] = oldIcon instanceof IconTransitionDrawable ?
                        ((IconTransitionDrawable) oldIcon).getDrawable(1) : oldIcon;
                layers[1] = newIcon;
                final IconTransitionDrawable itd = new IconTransitionDrawable(layers);
                v.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        itd.startTransition(ANIMATE_COMPONENT_CHANGE_DURATION);
                        v.setImageDrawable(itd);
                    }
                }, ANIMATE_COMPONENT_ICON_DELAY * i);
            } else {
                v.setImageDrawable(newIcon);
            }
        }
        if (pkgNameIdx > -1) {
            String pkgName = c.getString(pkgNameIdx);
            mSelectedComponentsMap.put(MODIFIES_ICONS, pkgName);
        }
    }

    private void loadNavBar(Cursor c, boolean animate) {
        int backButtonIdx = c.getColumnIndex(PreviewColumns.NAVBAR_BACK_BUTTON);
        int homeButtonIdx = c.getColumnIndex(PreviewColumns.NAVBAR_HOME_BUTTON);
        int recentButtonIdx = c.getColumnIndex(PreviewColumns.NAVBAR_RECENT_BUTTON);
        int backgroundIdx = c.getColumnIndex(NAVIGATION_BAR_BACKGROUND);
        if (backgroundIdx == -1) {
            backgroundIdx = c.getColumnIndex(PreviewColumns.STATUSBAR_BACKGROUND);
        }
        int pkgNameIdx = c.getColumnIndex(ThemesColumns.PKG_NAME);

        Bitmap background = Utils.loadBitmapBlob(c, backgroundIdx);
        Bitmap backButton = Utils.loadBitmapBlob(c, backButtonIdx);
        Bitmap homeButton = Utils.loadBitmapBlob(c, homeButtonIdx);
        Bitmap recentButton = Utils.loadBitmapBlob(c, recentButtonIdx);

        if (!mNavBar.isDrawingCacheEnabled()) mNavBar.setDrawingCacheEnabled(true);
        Drawable d = null;
        if (animate) {
            d = new BitmapDrawable(getResources(), mNavBar.getDrawingCache());
        }

        mNavBar.setBackground(new BitmapDrawable(getActivity().getResources(), background));
        mBackButton.setImageBitmap(backButton);
        mHomeButton.setImageBitmap(homeButton);
        mRecentButton.setImageBitmap(recentButton);

        if (pkgNameIdx > -1) {
            String pkgName = c.getString(pkgNameIdx);
            mSelectedComponentsMap.put(MODIFIES_NAVIGATION_BAR, pkgName);
        }
        if (animate) {
            animateContentChange(R.id.navigation_bar_container, mNavBar, d);
        }
    }

    private void loadFont(Cursor c, boolean animate) {
        if (!mFontPreview.isDrawingCacheEnabled()) mFontPreview.setDrawingCacheEnabled(true);
        Drawable d = null;
        if (animate) {
            d = new BitmapDrawable(getResources(), mFontPreview.getDrawingCache());
        }

        int pkgNameIdx = c.getColumnIndex(ThemesColumns.PKG_NAME);
        String pkgName = pkgNameIdx >= 0 ? c.getString(pkgNameIdx) : mPkgName;
        TypefaceHelperCache cache = TypefaceHelperCache.getInstance();
        ThemedTypefaceHelper helper = cache.getHelperForTheme(getActivity(),
                CURRENTLY_APPLIED_THEME.equals(pkgName) ? getAppliedFontPackageName() : pkgName);
        mTypefaceNormal = helper.getTypeface(Typeface.NORMAL);
        mFontPreview.setTypeface(mTypefaceNormal);
        if (pkgNameIdx > -1) {
            mSelectedComponentsMap.put(MODIFIES_FONTS, pkgName);
        }
        if (animate) {
            animateContentChange(R.id.font_preview_container, mFontPreview, d);
        }
    }

    public static ComponentName[] getIconComponents(Context context) {
        if (sIconComponents == null || sIconComponents.length == 0) {
            sIconComponents = new ComponentName[]{COMPONENT_DIALER, COMPONENT_MESSAGING,
                    COMPONENT_CAMERA, COMPONENT_BROWSER};

            PackageManager pm = context.getPackageManager();

            // if device does not have telephony replace dialer and mms
            if (!pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
                sIconComponents[0] = COMPONENT_CALENDAR;
                sIconComponents[1] = COMPONENT_GALERY;
            }

            if (!pm.hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
                sIconComponents[2] = COMPONENT_SETTINGS;
            } else {
                // decide on which camera icon to use
                try {
                    if (pm.getPackageInfo(CAMERA_NEXT_PACKAGE, 0) != null) {
                        sIconComponents[2] = COMPONENT_CAMERANEXT;
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    // default to COMPONENT_CAMERA
                }
            }

        }
        return sIconComponents;
    }

    private void initCards(View parent) {
        for (int i = 0; i < sCardIdsToComponentTypes.size(); i++) {
            parent.findViewById(sCardIdsToComponentTypes.keyAt(i))
                    .setOnClickListener(mCardClickListener);
        }
    }

    private View.OnClickListener mCardClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mActiveCardId > 0) {
                mActiveCardId = -1;
                getActivity().onBackPressed();
                return;
            }
            mActiveCardId = v.getId();
            String component = sCardIdsToComponentTypes.get(mActiveCardId);
            ((ChooserActivity) getActivity()).showComponentSelector(component, v);
            fadeOutNonSelectedCards(mActiveCardId);
        }
    };

    private OnItemClickedListener mOnComponentItemClicked = new OnItemClickedListener() {
        @Override
        public void onItemClicked(String pkgName) {
            Bundle args = new Bundle();
            args.putString("pkgName", pkgName);
            int loaderId = -1;
            String component = mSelector.getComponentType();
            if (MODIFIES_STATUS_BAR.equals(component)) {
                loaderId = LOADER_ID_STATUS_BAR;
            } else if (MODIFIES_FONTS.equals(component)) {
                loaderId = LOADER_ID_FONT;
            } else if (MODIFIES_ICONS.equals(component)) {
                loaderId = LOADER_ID_ICONS;
            } else if (MODIFIES_NAVIGATION_BAR.equals(component)) {
                loaderId = LOADER_ID_NAVIGATION_BAR;
            } else if (MODIFIES_LAUNCHER.equals(component)) {
                loaderId = LOADER_ID_WALLPAPER;
            } else {
                return;
            }
            getLoaderManager().restartLoader(loaderId, args, ThemeFragment.this);
        }
    };

    private void fadeOutNonSelectedCards(int selectedCardId) {
        for (int i = 0; i < sCardIdsToComponentTypes.size(); i++) {
            if (sCardIdsToComponentTypes.keyAt(i) != selectedCardId) {
                ComponentCardView card = (ComponentCardView) getView().findViewById(
                        sCardIdsToComponentTypes.keyAt(i));
                card.animateCardFadeOut();
            }
        }
    }

    private void animateContentChange(int parentId, View viewToAnimate, Drawable overlay) {
        ((ComponentCardView) getView().findViewById(parentId))
                .animateContentChange(viewToAnimate, overlay, ANIMATE_COMPONENT_CHANGE_DURATION);
    }

    private Runnable mApplyThemeRunnable = new Runnable() {
        @Override
        public void run() {
            final Context context = getActivity();
            if (context != null) {
                if (mSelectedComponentsMap != null && mSelectedComponentsMap.size() > 0) {
                    // Post this on mHandler so the client is added and removed from the same
                    // thread
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            ThemeManager tm = getThemeManager();
                            if (tm != null) {
                                tm.addClient(ThemeFragment.this);
                                tm.requestThemeChange(mSelectedComponentsMap);
                            }
                        }
                    });
                }
            }
        }
    };

    private void applyTheme() {
        if (mSelectedComponentsMap == null || mSelectedComponentsMap.size() <= 0) return;
        ((ChooserActivity) getActivity()).themeChangeStarted();
        animateProgressIn(mApplyThemeRunnable);
    }

    private void animateProgressIn(Runnable endAction) {
        mProgress.setVisibility(View.VISIBLE);
        mProgress.setProgress(0);
        float pivotX = mTitleLayout.getWidth() -
                getResources().getDimensionPixelSize(R.dimen.apply_progress_padding);
        ScaleAnimation scaleAnim = new ScaleAnimation(0f, 1f, 1f, 1f,
                pivotX, 0f);
        scaleAnim.setDuration(ANIMATE_PROGRESS_IN_DURATION);

        mTitleLayout.animate()
                .translationXBy(-(pivotX / 4))
                .alpha(0f)
                .setDuration(ANIMATE_TITLE_OUT_DURATION)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(endAction).start();
        mProgress.startAnimation(scaleAnim);
    }

    private void animateProgressOut() {
        mProgress.setVisibility(View.VISIBLE);
        float pivotX = mTitleLayout.getWidth() -
                getResources().getDimensionPixelSize(R.dimen.apply_progress_padding);
        ScaleAnimation scaleAnim = new ScaleAnimation(1f, 0f, 1f, 1f,
                pivotX, 0f);
        scaleAnim.setDuration(ANIMATE_PROGRESS_OUT_DURATION);
        scaleAnim.setFillAfter(false);
        scaleAnim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                mProgress.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });

        mTitleLayout.animate()
                .translationXBy((pivotX / 4))
                .alpha(1f)
                .setDuration(ANIMATE_TITLE_IN_DURATION)
                .setInterpolator(new AccelerateInterpolator())
                .start();
        mProgress.startAnimation(scaleAnim);
    }

    public void fadeInCards() {
        mActiveCardId = -1;
        for (int i = 0; i < sCardIdsToComponentTypes.size(); i++) {
            ComponentCardView card = (ComponentCardView) getView().findViewById(
                    sCardIdsToComponentTypes.keyAt(i));
            card.animateCardFadeIn();
        }
    }

    public boolean componentsChanged() {
        for (String key : mSelectedComponentsMap.keySet()) {
            if (!mPkgName.equals(mSelectedComponentsMap.get(key))) {
                return true;
            }
        }
        return false;
    }

    public void clearChanges() {
        mSelectedComponentsMap.clear();
        getLoaderManager().restartLoader(LOADER_ID_ALL, null, ThemeFragment.this);
    }
}