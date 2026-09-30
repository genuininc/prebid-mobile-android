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

package org.prebid.mobile.api.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What a VAST creative says about itself, for an app that draws its own UI around the ad the SDK
 * plays: the title, advertiser and description, where a tap goes (the click-through URL, the CTA
 * link), the media, companion images and icons — the brand's logo is usually one of them.
 * <p>
 * Read from the ad after every wrapper is followed: the creative's own details come from the
 * innermost InLine ad, which is the one that is played. VAST has no element for a CTA button's label
 * or a brand colour; those come from an optional {@code <Extension type="genuin">} carrying
 * {@code <CtaText>}, {@code <BrandLogo>} and {@code <BrandColor>}, and are null without one.
 * <p>
 * Everything here is display data. The SDK itself fires every VAST tracker (impression, quartiles,
 * click tracking) — an app that opens {@link #getClickThroughUrl()} itself must still report the
 * click through the ad view so the click trackers fire.
 */
public final class VastAdInfo {

    /** {@link #getSkipOffsetMs()} for an ad that cannot be skipped. */
    public static final long NOT_SKIPPABLE = -1;

    @Nullable private final String adId;
    @Nullable private final String adSystem;
    @Nullable private final String adTitle;
    @Nullable private final String advertiser;
    @Nullable private final String description;
    @Nullable private final String clickThroughUrl;
    private final long durationMs;
    private final long skipOffsetMs;
    @NonNull private final List<MediaFile> mediaFiles;
    @NonNull private final List<Image> companions;
    @NonNull private final List<Image> icons;
    @Nullable private final String ctaText;
    @Nullable private final String brandLogoUrl;
    @Nullable private final String brandColor;

    private VastAdInfo(Builder builder) {
        adId = builder.adId;
        adSystem = builder.adSystem;
        adTitle = builder.adTitle;
        advertiser = builder.advertiser;
        description = builder.description;
        clickThroughUrl = builder.clickThroughUrl;
        durationMs = builder.durationMs;
        skipOffsetMs = builder.skipOffsetMs;
        mediaFiles = Collections.unmodifiableList(new ArrayList<>(builder.mediaFiles));
        companions = Collections.unmodifiableList(new ArrayList<>(builder.companions));
        icons = Collections.unmodifiableList(new ArrayList<>(builder.icons));
        ctaText = builder.ctaText;
        brandLogoUrl = builder.brandLogoUrl;
        brandColor = builder.brandColor;
    }

    /** The {@code Ad} element's id. */
    @Nullable
    public String getAdId() {
        return adId;
    }

    /** The ad server that returned the ad ({@code AdSystem}). */
    @Nullable
    public String getAdSystem() {
        return adSystem;
    }

    /** The ad's title ({@code AdTitle}). */
    @Nullable
    public String getAdTitle() {
        return adTitle;
    }

    /** The advertiser's name ({@code Advertiser}). */
    @Nullable
    public String getAdvertiser() {
        return advertiser;
    }

    /** A longer description of the ad ({@code Description}). */
    @Nullable
    public String getDescription() {
        return description;
    }

    /** Where a tap on the ad goes — the CTA link ({@code VideoClicks/ClickThrough}). */
    @Nullable
    public String getClickThroughUrl() {
        return clickThroughUrl;
    }

    /** The linear creative's duration, in milliseconds; 0 when the VAST gives none. */
    public long getDurationMs() {
        return durationMs;
    }

    /** When the ad becomes skippable, in milliseconds, or {@link #NOT_SKIPPABLE}. */
    public long getSkipOffsetMs() {
        return skipOffsetMs;
    }

    /** Every media file the creative offers, in document order — video or audio. */
    @NonNull
    public List<MediaFile> getMediaFiles() {
        return mediaFiles;
    }

    /** The creative's companion images (static resources only), in document order. */
    @NonNull
    public List<Image> getCompanions() {
        return companions;
    }

    /** The linear creative's icons (static resources only), e.g. a brand logo or AdChoices. */
    @NonNull
    public List<Image> getIcons() {
        return icons;
    }

    /** The CTA button's label, from a Genuin extension; null when the creative has none. */
    @Nullable
    public String getCtaText() {
        return ctaText;
    }

    /** The advertiser's brand logo URL, from a Genuin extension; null when the creative has none. */
    @Nullable
    public String getBrandLogoUrl() {
        return brandLogoUrl;
    }

    /** The advertiser's brand colour (e.g. {@code #FF5A00}), from a Genuin extension. */
    @Nullable
    public String getBrandColor() {
        return brandColor;
    }

    /** One {@code MediaFile} of the linear creative. */
    public static final class MediaFile {
        @NonNull private final String url;
        @Nullable private final String type;
        @Nullable private final String delivery;
        private final int width;
        private final int height;
        private final int bitrateKbps;

        public MediaFile(
            @NonNull String url,
            @Nullable String type,
            @Nullable String delivery,
            int width,
            int height,
            int bitrateKbps
        ) {
            this.url = url;
            this.type = type;
            this.delivery = delivery;
            this.width = width;
            this.height = height;
            this.bitrateKbps = bitrateKbps;
        }

        @NonNull
        public String getUrl() {
            return url;
        }

        /** The MIME type, e.g. {@code video/mp4} or {@code audio/mpeg}. */
        @Nullable
        public String getType() {
            return type;
        }

        /** {@code progressive} or {@code streaming}. */
        @Nullable
        public String getDelivery() {
            return delivery;
        }

        /** In pixels; 0 when not given (always, for audio). */
        public int getWidth() {
            return width;
        }

        /** In pixels; 0 when not given (always, for audio). */
        public int getHeight() {
            return height;
        }

        /** In kbps; 0 when not given. */
        public int getBitrateKbps() {
            return bitrateKbps;
        }
    }

    /** A companion's or an icon's static image, and where tapping it goes. */
    public static final class Image {
        @NonNull private final String url;
        @Nullable private final String creativeType;
        private final int width;
        private final int height;
        @Nullable private final String clickThroughUrl;
        @Nullable private final String label;

        public Image(
            @NonNull String url,
            @Nullable String creativeType,
            int width,
            int height,
            @Nullable String clickThroughUrl,
            @Nullable String label
        ) {
            this.url = url;
            this.creativeType = creativeType;
            this.width = width;
            this.height = height;
            this.clickThroughUrl = clickThroughUrl;
            this.label = label;
        }

        @NonNull
        public String getUrl() {
            return url;
        }

        /** The image's MIME type, e.g. {@code image/png}. */
        @Nullable
        public String getCreativeType() {
            return creativeType;
        }

        /** In pixels; 0 when not given. */
        public int getWidth() {
            return width;
        }

        /** In pixels; 0 when not given. */
        public int getHeight() {
            return height;
        }

        @Nullable
        public String getClickThroughUrl() {
            return clickThroughUrl;
        }

        /** A companion's {@code AltText}, or an icon's {@code program} (e.g. {@code AdChoices}). */
        @Nullable
        public String getLabel() {
            return label;
        }
    }

    /** Assembles a {@link VastAdInfo}; see {@code VastAdInfoFactory} for how the SDK fills one. */
    public static final class Builder {
        private String adId;
        private String adSystem;
        private String adTitle;
        private String advertiser;
        private String description;
        private String clickThroughUrl;
        private long durationMs;
        private long skipOffsetMs = NOT_SKIPPABLE;
        private final List<MediaFile> mediaFiles = new ArrayList<>();
        private final List<Image> companions = new ArrayList<>();
        private final List<Image> icons = new ArrayList<>();
        private String ctaText;
        private String brandLogoUrl;
        private String brandColor;

        public Builder setAdId(@Nullable String adId) {
            this.adId = adId;
            return this;
        }

        public Builder setAdSystem(@Nullable String adSystem) {
            this.adSystem = adSystem;
            return this;
        }

        public Builder setAdTitle(@Nullable String adTitle) {
            this.adTitle = adTitle;
            return this;
        }

        public Builder setAdvertiser(@Nullable String advertiser) {
            this.advertiser = advertiser;
            return this;
        }

        public Builder setDescription(@Nullable String description) {
            this.description = description;
            return this;
        }

        public Builder setClickThroughUrl(@Nullable String clickThroughUrl) {
            this.clickThroughUrl = clickThroughUrl;
            return this;
        }

        public Builder setDurationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder setSkipOffsetMs(long skipOffsetMs) {
            this.skipOffsetMs = skipOffsetMs;
            return this;
        }

        public Builder addMediaFile(@NonNull MediaFile mediaFile) {
            mediaFiles.add(mediaFile);
            return this;
        }

        public Builder addCompanion(@NonNull Image companion) {
            companions.add(companion);
            return this;
        }

        public Builder addIcon(@NonNull Image icon) {
            icons.add(icon);
            return this;
        }

        public Builder setCtaText(@Nullable String ctaText) {
            this.ctaText = ctaText;
            return this;
        }

        public Builder setBrandLogoUrl(@Nullable String brandLogoUrl) {
            this.brandLogoUrl = brandLogoUrl;
            return this;
        }

        public Builder setBrandColor(@Nullable String brandColor) {
            this.brandColor = brandColor;
            return this;
        }

        @NonNull
        public VastAdInfo build() {
            return new VastAdInfo(this);
        }
    }
}
