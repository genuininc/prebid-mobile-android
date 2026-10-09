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
import android.content.res.XmlResourceParser;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Xml;
import android.view.TextureView;
import android.view.View;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.PlayerView;

import org.prebid.mobile.LogUtil;
import org.prebid.mobile.api.exceptions.AdException;
import org.prebid.mobile.configuration.AdUnitConfiguration;
import org.prebid.mobile.core.R;
import org.prebid.mobile.rendering.listeners.VideoCreativeViewListener;
import org.prebid.mobile.rendering.video.vast.VASTErrorCodes;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

public class ExoPlayerView extends PlayerView implements VideoPlayerView {

    private static final String TAG = "ExoPlayerView";
    public static final float DEFAULT_INITIAL_VIDEO_VOLUME = 1.0f;

    @NonNull private final VideoCreativeViewListener videoCreativeViewListener;
    private AdViewProgressUpdateTask adViewProgressUpdateTask;
    private AdUnitConfiguration config;
    private ExoPlayer player;

    private Uri videoUri;

    private long vastVideoDuration = -1;

    /**
     * Paused by {@link #pause()}. The player stays prepared while paused, so {@link #resume()} plays on
     * from the same frame at once; this keeps it paused when it turns ready meanwhile.
     */
    private boolean isPaused = false;

    /**
     * The video's SurfaceTexture while the view is out of a window, kept rather than released (see
     * {@link RetainingSurfaceListener}) and handed back to the TextureView when it is attached again.
     */
    @Nullable private SurfaceTexture retainedSurfaceTexture;

    /**
     * A kept texture being handed back to the view on its next frame, after its window made it a new
     * one without taking the view out ({@link RetainingSurfaceListener#onSurfaceTextureAvailable}).
     */
    @Nullable private SurfaceTexture restoringSurfaceTexture;

    public ExoPlayerView(
            Context context,
            @NonNull VideoCreativeViewListener videoCreativeViewListener
    ) {
        super(context, playerViewAttributes(context));
        this.videoCreativeViewListener = videoCreativeViewListener;
    }

    /**
     * The attributes in {@code res/xml/prebid_exo_player_view.xml}: a TextureView surface, so the
     * video keeps its fit when the view is moved to another window (see that file).
     */
    @Nullable
    private static AttributeSet playerViewAttributes(Context context) {
        XmlResourceParser parser = context.getResources().getXml(R.xml.prebid_exo_player_view);
        try {
            int type;
            do {
                type = parser.next();
            } while (type != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT);
            return type == XmlPullParser.START_TAG ? Xml.asAttributeSet(parser) : null;
        } catch (XmlPullParserException | IOException e) {
            LogUtil.error(TAG, "Player view attributes unreadable, using the defaults: " + e.getMessage());
            return null;
        }
    }

    private final Player.Listener eventListener = new Player.Listener() {

        @Override
        public void onPlayerError(PlaybackException error) {
            videoCreativeViewListener.onFailure(new AdException(
                    AdException.INTERNAL_ERROR,
                    VASTErrorCodes.MEDIA_DISPLAY_ERROR.toString()
            ));
        }

        @Override
        public void onPlaybackStateChanged(int playbackState) {
            if (player == null) {
                LogUtil.debug(TAG, "onPlayerStateChanged(): Skipping state handling. Player is null");
                return;
            }
            switch (playbackState) {
                case Player.STATE_READY:
                    player.setPlayWhenReady(!isPaused);
                    initUpdateTask();
                    break;
                case Player.STATE_ENDED:
                    videoCreativeViewListener.onDisplayCompleted();
                    break;
            }
        }
    };

    @Override
    public void mute() {
        setVolume(0);
    }

    @Override
    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    @Override
    public void unMute() {
        setVolume(DEFAULT_INITIAL_VIDEO_VOLUME);
    }

    @Override
    public void start(float initialVolume) {
        LogUtil.debug(TAG, "Called start");
        initLayout();
        initPlayer(initialVolume);
        preparePlayer(true);
        trackInitialStartEvent();
    }

    @Override
    public void setVastVideoDuration(long duration) {
        vastVideoDuration = duration;
    }

    @Override
    public long getCurrentPosition() {
        if (player == null) {
            return -1;
        }
        return player.getContentPosition();
    }

    @Override
    public void setVideoUri(Uri uri) {
        videoUri = uri;
    }

    /**
     * The media's duration in ms, or -1 while it is unknown: before the player has prepared, and after
     * it is released — which happens as a video ends, before the completion is reported.
     */
    @Override
    public int getDuration() {
        if (player == null) {
            return -1;
        }
        long duration = player.getDuration();
        return duration == C.TIME_UNSET ? -1 : (int) duration;
    }

    @Override
    public float getVolume() {
        return player.getVolume();
    }

    void setAdUnitConfiguration(AdUnitConfiguration config) {
        this.config = config;
    }

    /**
     * Plays on from where {@link #pause()} left it — at once, the player is still prepared — or
     * prepares it again where it is not (stopped, or failed).
     */
    @Override
    public void resume() {
        LogUtil.debug(TAG, "Called resume");
        isPaused = false;
        if (player != null && videoUri != null && isPrepared()) {
            player.setPlayWhenReady(true);
        } else {
            preparePlayer(false);
        }
        videoCreativeViewListener.onEvent(VideoAdEvent.Event.AD_RESUME);
    }

    /**
     * Pauses without stopping: the player keeps its media and its last frame on screen, so a resume
     * neither re-buffers nor blanks the video — a window-focus change pauses and resumes it, as when
     * the app's fullscreen page opens over it.
     */
    @Override
    public void pause() {
        LogUtil.debug(TAG, "Called pause");
        if (player != null) {
            isPaused = true;
            player.setPlayWhenReady(false);
            videoCreativeViewListener.onEvent(VideoAdEvent.Event.AD_PAUSE);
        }
    }

    private boolean isPrepared() {
        int state = player.getPlaybackState();
        return state == Player.STATE_READY || state == Player.STATE_BUFFERING;
    }

    @Override
    public void forceStop() {
        destroy();
        videoCreativeViewListener.onDisplayCompleted();
    }

    @Override
    public void destroy() {
        LogUtil.debug(TAG, "Called destroy");
        killUpdateTask();
        if (player != null) {
            player.stop();
            player.removeListener(eventListener);
            restorePlayerSurfaceListener();
            setPlayer(null);
            player.release();
            player = null;
        }
        if (retainedSurfaceTexture != null) {
            retainedSurfaceTexture.release();
            retainedSurfaceTexture = null;
        }
        if (restoringSurfaceTexture != null) {
            restoringSurfaceTexture.release();
            restoringSurfaceTexture = null;
        }
    }

    @VisibleForTesting
    void setVolume(float volume) {
        if (player != null && volume >= 0.0f) {
            videoCreativeViewListener.onVolumeChanged(volume);
            player.setVolume(volume);
        }
    }

    @Override
    public void stop() {
        if (player != null) {
            player.stop();
            player.clearMediaItems();
        }
    }

    private void initLayout() {
        RelativeLayout.LayoutParams playerLayoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT);
        playerLayoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        setLayoutParams(playerLayoutParams);
    }

    private void initPlayer(float initialVolume) {
        if (player != null) {
            LogUtil.debug(TAG, "Skipping initPlayer(): Player is already initialized.");
            return;
        }
        player = new ExoPlayer.Builder(getContext()).build();
        player.addListener(eventListener);
        setPlayer(this.player);
        retainSurfaceAcrossWindows();
        setUseController(false);
        player.setVolume(initialVolume);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Before the TextureView draws in this window, so it draws the kept texture rather than
        // creating a new, empty one.
        TextureView textureView = textureView();
        if (retainedSurfaceTexture != null && textureView != null) {
            textureView.setSurfaceTexture(retainedSurfaceTexture);
            retainedSurfaceTexture = null;
        }
    }

    /**
     * Keeps the video's surface while the view moves between windows — from the app's feed to its
     * fullscreen page and back. A TextureView releases its SurfaceTexture when it leaves a window and
     * makes an empty one in the next, so the video would go black there and the player, its surface
     * gone, would draw nothing until it got the new one. Wrapping the player's listener keeps the
     * texture instead: the player goes on drawing into it, and the view shows it the moment it is
     * attached again ({@link #onAttachedToWindow()}).
     */
    @VisibleForTesting
    void retainSurfaceAcrossWindows() {
        TextureView textureView = textureView();
        if (textureView == null) {
            return;
        }
        TextureView.SurfaceTextureListener playerListener = textureView.getSurfaceTextureListener();
        if (playerListener == null || playerListener instanceof RetainingSurfaceListener) {
            return;
        }
        textureView.setSurfaceTextureListener(new RetainingSurfaceListener(playerListener));
    }

    /**
     * Hands the kept texture back to the view, which releases the empty one it made — or keeps it on
     * for the view's next window, where the view left this one before the hand-back.
     */
    private void restoreKeptSurface() {
        SurfaceTexture kept = restoringSurfaceTexture;
        restoringSurfaceTexture = null;
        if (kept == null) {
            return;
        }
        TextureView textureView = textureView();
        if (textureView != null && textureView.isAvailable()) {
            textureView.setSurfaceTexture(kept);
        } else {
            retainedSurfaceTexture = kept;
        }
    }

    /** Gives the player its own listener back, for it to remove as it lets go of the view. */
    private void restorePlayerSurfaceListener() {
        TextureView textureView = textureView();
        if (textureView != null
                && textureView.getSurfaceTextureListener() instanceof RetainingSurfaceListener) {
            textureView.setSurfaceTextureListener(
                    ((RetainingSurfaceListener) textureView.getSurfaceTextureListener()).playerListener
            );
        }
    }

    @Nullable
    private TextureView textureView() {
        View surface = getVideoSurfaceView();
        return surface instanceof TextureView ? (TextureView) surface : null;
    }

    /**
     * The player's own SurfaceTexture listener, except that a texture leaving its window is kept for
     * the view's next one ({@link #retainedSurfaceTexture}) — and not reported to the player, which
     * would let go of its surface.
     *
     * A window can also take the texture away without taking the view out — it drops its drawing
     * resources while the app's page is in the background — and make it a new, empty one on the
     * way back. The player stays on the kept texture then too, which still has its last frame, and
     * the view takes that back on its next frame.
     */
    private final class RetainingSurfaceListener implements TextureView.SurfaceTextureListener {

        private final TextureView.SurfaceTextureListener playerListener;

        RetainingSurfaceListener(TextureView.SurfaceTextureListener playerListener) {
            this.playerListener = playerListener;
        }

        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            TextureView textureView = textureView();
            if (retainedSurfaceTexture != null && textureView != null) {
                // Not from inside this callback, which the view makes while it draws.
                restoringSurfaceTexture = retainedSurfaceTexture;
                retainedSurfaceTexture = null;
                textureView.post(ExoPlayerView.this::restoreKeptSurface);
                return;
            }
            playerListener.onSurfaceTextureAvailable(surface, width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
            playerListener.onSurfaceTextureSizeChanged(surface, width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            if (restoringSurfaceTexture != null) {
                // The empty texture made while a kept one waited to be handed back: let it go.
                return true;
            }
            retainedSurfaceTexture = surface;
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
            playerListener.onSurfaceTextureUpdated(surface);
        }
    }

    private void initUpdateTask() {
        if (adViewProgressUpdateTask != null) {
            LogUtil.debug(TAG, "initUpdateTask: AdViewProgressUpdateTask is already initialized. Skipping.");
            return;
        }

        try {
            adViewProgressUpdateTask = new AdViewProgressUpdateTask(
                    videoCreativeViewListener,
                    (int) player.getDuration(),
                    config
            );
            adViewProgressUpdateTask.setVastTagDuration(vastVideoDuration);
            adViewProgressUpdateTask.execute();
        }
        catch (AdException e) {
            e.printStackTrace();
        }
    }

    @VisibleForTesting
    void preparePlayer(boolean resetPosition) {
        ProgressiveMediaSource extractorMediaSource = buildMediaSource(videoUri);
        if (extractorMediaSource == null || player == null) {
            LogUtil.debug(TAG, "preparePlayer(): ProgressiveMediaSource or ExoPlayer is null. Skipping prepare.");
            return;
        }
        player.setMediaSource(extractorMediaSource, resetPosition);
        player.prepare();
    }

    private ProgressiveMediaSource buildMediaSource(Uri uri) {
        if (uri == null) {
            return null;
        }
        MediaItem mediaItem = new MediaItem.Builder().setUri(uri).build();
        return new ProgressiveMediaSource.Factory(
                new DefaultDataSource.Factory(getContext()))
                .createMediaSource(mediaItem);
    }

    private void killUpdateTask() {
        if (adViewProgressUpdateTask != null) {
            adViewProgressUpdateTask.cancel(true);
            adViewProgressUpdateTask = null;
        }
    }

    private void trackInitialStartEvent() {
        if (videoUri != null && player != null && player.getCurrentPosition() == 0) {
            videoCreativeViewListener.onEvent(VideoAdEvent.Event.AD_CREATIVEVIEW);
            videoCreativeViewListener.onEvent(VideoAdEvent.Event.AD_START);
        }
    }
}
