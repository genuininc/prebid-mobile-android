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

package org.prebid.mobile;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The OpenRTB 2.x {@code imp.audio} object an audio ad unit sends, for {@link
 * org.prebid.mobile.api.data.AdUnitFormat#AUDIO}. Every field is optional: an ad unit that sets none
 * still asks for the audio types and VAST versions the SDK's own player handles.
 */
public class AudioParameters {

    /** The audio MIME types, e.g. {@code audio/mp4}, {@code audio/mpeg}. Empty for the SDK's own. */
    @NonNull
    private List<String> mimes;

    /** Minimum audio ad duration in seconds. */
    @Nullable
    private Integer minDuration;

    /** Maximum audio ad duration in seconds. */
    @Nullable
    private Integer maxDuration;

    /** Minimum bitrate in kbps. */
    @Nullable
    private Integer minBitrate;

    /** Maximum bitrate in kbps. */
    @Nullable
    private Integer maxBitrate;

    /** Supported VAST protocols. Null for the SDK's own (VAST 2.0 to 4.x, inline and wrapper). */
    @Nullable
    private List<Signals.Protocols> protocols;

    /** Start delay of the ad (pre-, mid- or post-roll). */
    @Nullable
    private Signals.StartDelay startDelay;

    /** Supported API frameworks. */
    @Nullable
    private List<Signals.Api> api;

    /** Blocked creative attributes. */
    @Nullable
    private List<Signals.CreativeAttribute> battr;

    /** Audio parameters asking for the SDK's own supported audio types. */
    public AudioParameters() {
        this(new ArrayList<>());
    }

    public AudioParameters(@NonNull List<String> mimes) {
        this.mimes = mimes;
    }

    @NonNull
    public List<String> getMimes() {
        return mimes;
    }

    public void setMimes(@NonNull List<String> mimes) {
        this.mimes = mimes;
    }

    @Nullable
    public Integer getMinDuration() {
        return minDuration;
    }

    public void setMinDuration(@Nullable Integer minDuration) {
        this.minDuration = minDuration;
    }

    @Nullable
    public Integer getMaxDuration() {
        return maxDuration;
    }

    public void setMaxDuration(@Nullable Integer maxDuration) {
        this.maxDuration = maxDuration;
    }

    @Nullable
    public Integer getMinBitrate() {
        return minBitrate;
    }

    public void setMinBitrate(@Nullable Integer minBitrate) {
        this.minBitrate = minBitrate;
    }

    @Nullable
    public Integer getMaxBitrate() {
        return maxBitrate;
    }

    public void setMaxBitrate(@Nullable Integer maxBitrate) {
        this.maxBitrate = maxBitrate;
    }

    @Nullable
    public List<Signals.Protocols> getProtocols() {
        return protocols;
    }

    public void setProtocols(@Nullable List<Signals.Protocols> protocols) {
        this.protocols = protocols;
    }

    @Nullable
    public Signals.StartDelay getStartDelay() {
        return startDelay;
    }

    public void setStartDelay(@Nullable Signals.StartDelay startDelay) {
        this.startDelay = startDelay;
    }

    @Nullable
    public List<Signals.Api> getApi() {
        return api;
    }

    public void setApi(@Nullable List<Signals.Api> api) {
        this.api = api;
    }

    @Nullable
    public List<Signals.CreativeAttribute> getBattr() {
        return battr;
    }

    public void setBattr(@Nullable List<Signals.CreativeAttribute> battr) {
        this.battr = battr;
    }
}
