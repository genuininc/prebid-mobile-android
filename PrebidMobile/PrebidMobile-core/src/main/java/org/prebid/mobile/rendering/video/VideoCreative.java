/*
 *    Copyright 2018-2021 Prebid.org, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.prebid.mobile.rendering.video;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import org.prebid.mobile.LogUtil;
import org.prebid.mobile.api.exceptions.AdException;
import org.prebid.mobile.configuration.AdUnitConfiguration;
import org.prebid.mobile.rendering.interstitial.InterstitialManagerVideoDelegate;
import org.prebid.mobile.rendering.listeners.CreativeViewListener;
import org.prebid.mobile.rendering.listeners.VideoCreativeViewListener;
import org.prebid.mobile.rendering.loading.FileDownloadListener;
import org.prebid.mobile.rendering.models.CreativeVisibilityTracker;
import org.prebid.mobile.rendering.models.TrackingEvent;
import org.prebid.mobile.rendering.models.internal.InternalPlayerState;
import org.prebid.mobile.rendering.models.internal.VisibilityTrackerOption;
import org.prebid.mobile.rendering.models.ntv.NativeEventTracker;
import org.prebid.mobile.rendering.networking.BaseNetworkTask;
import org.prebid.mobile.rendering.session.manager.OmAdSessionManager;
import org.prebid.mobile.rendering.utils.helpers.AppInfoManager;
import org.prebid.mobile.rendering.utils.helpers.Utils;
import org.prebid.mobile.rendering.views.interstitial.InterstitialManager;

import java.io.File;
import java.lang.ref.WeakReference;

public class VideoCreative extends VideoCreativeProtocol
    implements VideoCreativeViewListener, InterstitialManagerVideoDelegate {
    private static final String TAG = VideoCreative.class.getSimpleName();

    @NonNull private final VideoCreativeModel model;

    @VisibleForTesting VideoCreativeView videoCreativeView;

    /** An audio creative's impression, fired once when its playback starts. */
    private boolean audioImpressionTracked;

    private AsyncTask videoDownloadTask;

    private String preloadedVideoFilePath;

    public VideoCreative(Context context,
                         @NonNull
                             VideoCreativeModel model, OmAdSessionManager omAdSessionManager, InterstitialManager interstitialManager)
    throws AdException {
        super(context, model, omAdSessionManager, interstitialManager);

        this.model = model;
        if (this.interstitialManager != null) {
            this.interstitialManager.setInterstitialVideoDelegate(this);
        }
    }

    @Override
    public void load() {
        //Use URLConnection to download a video file.
        BaseNetworkTask.GetUrlParams params = new BaseNetworkTask.GetUrlParams();

        params.url = model.getMediaUrl();
        params.userAgent = AppInfoManager.getUserAgent();
        params.requestType = "GET";
        params.name = BaseNetworkTask.DOWNLOAD_TASK;

        Context context = contextReference.get();
        if (context != null) {
            AdUnitConfiguration adConfiguration = model.getAdConfiguration();
            String shortenedPath = LruController.getShortenedPath(params.url);
            File file = new File(context.getFilesDir(), shortenedPath);
            VideoDownloadTask videoDownloadTask = new VideoDownloadTask(context, file,
                                                                        new VideoCreativeVideoPreloadListener(this), adConfiguration);
            this.videoDownloadTask = videoDownloadTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
        }
    }

    @Override
    public void display() {
        if (videoCreativeView != null) {
            videoCreativeView.start(model.getAdConfiguration().getVideoInitialVolume());

            setStartIsMutedValue(model.getAdConfiguration().isMuted());

            model.trackPlayerStateChange(InternalPlayerState.NORMAL);
            // An audio ad has nothing to watch, so its view's visibility says nothing about whether
            // it was heard: its impression counts when playback starts instead (see onEvent).
            if (!model.isAudio()) {
                startViewabilityTracker();
            }
        }
    }

    @Override
    public void skip() {
        LogUtil.debug(TAG, "Track 'skip' event");
        model.trackVideoEvent(VideoAdEvent.Event.AD_SKIP);
        // Send it to AdView
        getCreativeViewListener().creativeDidComplete(this);
    }

    @Override
    public void onReadyForDisplay() {
        getResolutionListener().creativeReady(this);
    }

    @Override
    public void onDisplayCompleted() {
        complete();
    }

    @Override
    public void onFailure(AdException error) {
        // ad -> inline -> error
        model.trackVideoEvent(VideoAdEvent.Event.AD_ERROR);
        getResolutionListener().creativeFailed(error);
    }

    @Override
    public void onEvent(VideoAdEvent.Event trackingEvent) {
        model.trackVideoEvent(trackingEvent);

        if (trackingEvent == VideoAdEvent.Event.AD_START && model.isAudio() && !audioImpressionTracked) {
            audioImpressionTracked = true;
            model.trackVideoEvent(VideoAdEvent.Event.AD_IMPRESSION);
        }

        notifyCreativeViewListener(trackingEvent);
    }

    @Override
    public void trackAdLoaded() {
        model.trackNonSkippableStandaloneVideoLoaded(false);
    }

    @Override
    public void onVolumeChanged(float volume) {
        notifyVolumeChanged(volume);

        OmAdSessionManager omAdSessionManager = weakOmAdSessionManager.get();
        if (omAdSessionManager == null) {
            LogUtil.error(TAG, "trackVolume failed, OmAdSessionManager is null");
            return;
        }
        omAdSessionManager.trackVolumeChange(volume);
    }

    @Override
    public void onPlayerStateChanged(InternalPlayerState state) {
        model.trackPlayerStateChange(state);
    }

    @Override
    public boolean isInterstitialClosed() {
        return model.hasEndCard();
    }

    @Override
    public long getMediaDuration() {
        return model.getMediaDuration();
    }

    @Override
    public void handleAdWindowFocus() {
        // Resume video view
        resume();
    }

    @Override
    public void resume() {
        if (videoCreativeView != null && videoCreativeView.hasVideoStarted()) {
            videoCreativeView.resume();
        }
    }

    @Override
    public boolean isPlaying() {
        return videoCreativeView != null && videoCreativeView.isPlaying();
    }

    @Override
    public void handleAdWindowNoFocus() {
        // Pause video view
        pause();
    }

    @Override
    public void pause() {
        if (videoCreativeView != null && videoCreativeView.isPlaying()) {
            videoCreativeView.pause();
        }
    }

    @Override
    public void mute() {
        if (videoCreativeView != null) {
            videoCreativeView.mute();
        }
    }

    @Override
    public boolean isMuted() {
        return videoCreativeView != null && videoCreativeView.isMuted();
    }

    @Override
    public long getPlaybackPositionMs() {
        return videoCreativeView == null ? -1 : videoCreativeView.getPlaybackPositionMs();
    }

    /** The player's duration while it knows one, else the VAST's. */
    @Override
    public long getPlaybackDurationMs() {
        long fromPlayer = videoCreativeView == null ? -1 : videoCreativeView.getPlaybackDurationMs();
        return fromPlayer > 0 ? fromPlayer : getMediaDuration();
    }

    @Override
    public boolean performClick() {
        return videoCreativeView != null && videoCreativeView.performCallToActionClick();
    }

    /**
     * The viewer's skip from an app's own skip control: allowed only for a VAST with a
     * {@code skipoffset}, once the player has reached it (or while a paused player cannot say where
     * it is — the app's own control is what waits out the offset then). Stops the player, then skips
     * as Prebid's own control does: the VAST skip tracker fires and the creative completes.
     */
    @Override
    public boolean performSkip() {
        long skipOffsetMs = model.getSkipOffset();
        if (videoCreativeView == null || skipOffsetMs < 0) {
            return false;
        }
        long positionMs = videoCreativeView.getPlaybackPositionMs();
        if (positionMs >= 0 && positionMs < skipOffsetMs) {
            return false;
        }
        videoCreativeView.stop();
        skip();
        return true;
    }

    @Override
    public void unmute() {
        if (videoCreativeView != null) {
            videoCreativeView.unMute();
        }
    }

    private void setStartIsMutedValue(boolean isMuted) {
        if (videoCreativeView != null && videoCreativeView.getVolume() == 0) {
            videoCreativeView.setStartIsMutedProperty(isMuted);
        }
    }

    @Override
    public void createOmAdSession() {
        OmAdSessionManager omAdSessionManager = weakOmAdSessionManager.get();
        if (omAdSessionManager == null) {
            LogUtil.error(TAG, "Error creating AdSession. OmAdSessionManager is null");
            return;
        }

        omAdSessionManager.initVideoAdSession(model.getAdVerifications(), null);
        startOmSession();
    }

    @Override
    public void startViewabilityTracker() {
        VisibilityTrackerOption visibilityTrackerOption = new VisibilityTrackerOption(NativeEventTracker.EventType.IMPRESSION);

        creativeVisibilityTracker = new CreativeVisibilityTracker(getCreativeView(), visibilityTrackerOption);
        creativeVisibilityTracker.setVisibilityTrackerListener((result) -> {
            if (result.isVisible() && result.shouldFireImpression()) {
                model.trackVideoEvent(VideoAdEvent.Event.AD_IMPRESSION);
                creativeVisibilityTracker.stopVisibilityCheck();
                creativeVisibilityTracker = null;
            }
        });
        creativeVisibilityTracker.startVisibilityCheck(contextReference.get());
    }

    @Override
    public void destroy() {
        super.destroy();

        if (videoCreativeView != null) {
            videoCreativeView.destroy();
        }

        if (videoDownloadTask != null) {
            videoDownloadTask.cancel(true);
        }
    }

    @Override
    public boolean isDisplay() {
        return false;
    }

    @Override
    public boolean isVideo() {
        return true;
    }

    /**
     * @return true if {@link #preloadedVideoFilePath} is not empty and file exists in filesDir, false otherwise.
     */
    @Override
    public boolean isResolved() {
        if (contextReference.get() != null && !TextUtils.isEmpty(preloadedVideoFilePath)) {
            File file = new File(contextReference.get().getFilesDir(), preloadedVideoFilePath);
            return file.exists();
        }
        return false;
    }

    @Override
    public boolean isEndCard() {
        return false;
    }

    @Override
    public void onVideoInterstitialClosed() {
        if (videoCreativeView != null) {
            videoCreativeView.destroy();
        }
        if (getCreativeViewListener() != null) {
            getCreativeViewListener().creativeDidComplete(this);
        }
    }

    public long getVideoSkipOffset() {
        return model.getSkipOffset();
    }

    @Override
    public void trackVideoEvent(VideoAdEvent.Event event) {
        model.trackVideoEvent(event);
    }

    @Override
    @NonNull
    public VideoCreativeModel getCreativeModel() {
        return model;
    }

    private void loadContinued() {
        try {
            createCreativeView();
        }
        catch (AdException e) {
            getResolutionListener().creativeFailed(e);
            return;
        }
        setCreativeView(videoCreativeView);
        //VideoView has been created. Send adDidLoad() to pubs
        onReadyForDisplay();
    }

    // Helper method to reduce duplicate code in the subclass RewardedVideoCreative
    private void createCreativeView() throws AdException {
        Uri videoUri = null;

        final Context context = contextReference.get();
        if (context != null) {
            final AdUnitConfiguration adConfiguration = model.getAdConfiguration();
            videoCreativeView = new VideoCreativeView(context, this, adConfiguration);
            videoCreativeView.setBroadcastId(adConfiguration.getBroadcastId());

            // Get the preloaded video from device file storage
            videoUri = Uri.fromFile(new File(context.getFilesDir() + (model.getMediaUrl())));
        }

        // Show call-to-action overlay right away if click through url is available & end card is not available
        showCallToAction();

        videoCreativeView.setCallToActionUrl(model.getVastClickthroughUrl());
        videoCreativeView.setVastVideoDuration(getMediaDuration());
        videoCreativeView.setVideoUri(videoUri);
    }

    private void startOmSession() {
        OmAdSessionManager omAdSessionManager = weakOmAdSessionManager.get();

        if (omAdSessionManager == null) {
            LogUtil.error(TAG, "startOmSession: Failed. omAdSessionManager is null");
            return;
        }

        if (videoCreativeView == null) {
            LogUtil.error(TAG, "startOmSession: Failed. VideoCreativeView is null");
            return;
        }

        startOmSession(omAdSessionManager, (View) videoCreativeView.getVideoPlayerView());
        model.registerActiveOmAdSession(omAdSessionManager);
    }

    private void trackVideoAdStart() {
        if (videoCreativeView == null || videoCreativeView.getVideoPlayerView() == null) {
            LogUtil.error(TAG, "trackVideoAdStart error. videoCreativeView or VideoPlayerView is null.");
            return;
        }

        VideoPlayerView plugPlayVideoView = videoCreativeView.getVideoPlayerView();
        // The start is tracked as playback begins, often before the player knows the media's length
        // (its getDuration() is then -1). Open Measurement throws on a duration that is not positive,
        // on the main thread, so the VAST's <Duration> stands in until the player has one, and the
        // start is not reported to it at all when neither is known.
        long durationMs = plugPlayVideoView.getDuration();
        if (durationMs <= 0) {
            durationMs = getMediaDuration();
        }
        if (durationMs <= 0) {
            LogUtil.error(TAG, "trackVideoAdStart: media duration unknown, Open Measurement start not tracked.");
            return;
        }
        float volume = plugPlayVideoView.getVolume();

        model.trackVideoAdStarted(durationMs, volume);
    }

    protected void complete() {
        LogUtil.debug(TAG, "Track 'complete' event");

        model.trackVideoEvent(VideoAdEvent.Event.AD_COMPLETE);

        if (videoCreativeView != null) {
            videoCreativeView.hideVolumeControls();
        }

        // Send it to AdView
        getCreativeViewListener().creativeDidComplete(this);
    }

    protected void showCallToAction() {
        if (!model.getAdConfiguration().isBuiltInVideo()
                && Utils.isNotBlank(model.getVastClickthroughUrl())
        ) {
            videoCreativeView.showCallToAction(true);
        }
    }

    private void notifyCreativeViewListener(VideoAdEvent.Event trackingEvent) {
        final CreativeViewListener creativeViewListener = getCreativeViewListener();

        switch (trackingEvent) {
            case AD_START:
                trackVideoAdStart();
                model.trackEventNamed(TrackingEvent.Events.IMPRESSION);
                break;
            case AD_CLICK:
                creativeViewListener.creativeWasClicked(this, videoCreativeView.getCallToActionUrl());
                break;
            case AD_RESUME:
                creativeViewListener.creativeResumed(this);
                break;
            case AD_PAUSE:
                creativeViewListener.creativePaused(this);
                break;
        }
    }

    private void notifyVolumeChanged(float volume) {
        final CreativeViewListener creativeViewListener = getCreativeViewListener();

        if (volume == 0) {
            creativeViewListener.creativeMuted(this);
        }
        else {
            creativeViewListener.creativeUnMuted(this);
        }
    }

    private static class VideoCreativeVideoPreloadListener implements FileDownloadListener {

        private WeakReference<VideoCreative> weakVideoCreative;

        VideoCreativeVideoPreloadListener(VideoCreative videoCreative) {
            weakVideoCreative = new WeakReference<>(videoCreative);
        }

        @Override
        public void onFileDownloaded(String shortenedPath) {
            VideoCreative videoCreative = weakVideoCreative.get();
            if (videoCreative == null) {
                LogUtil.warning(TAG, "VideoCreative is null");
                return;
            }

            videoCreative.preloadedVideoFilePath = shortenedPath;
            videoCreative.model.setMediaUrl(shortenedPath);
            videoCreative.loadContinued();
        }

        @Override
        public void onFileDownloadError(String error) {
            VideoCreative videoCreative = weakVideoCreative.get();
            if (videoCreative == null) {
                LogUtil.warning(TAG, "VideoCreative is null");
                return;
            }

            videoCreative.getResolutionListener().creativeFailed(new AdException(AdException.INTERNAL_ERROR, "Preloading failed: " + error));
        }
    }
}
