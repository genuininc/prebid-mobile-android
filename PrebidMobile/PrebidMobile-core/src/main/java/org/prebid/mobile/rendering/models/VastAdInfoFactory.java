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

package org.prebid.mobile.rendering.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.prebid.mobile.api.data.VastAdInfo;
import org.prebid.mobile.rendering.parser.AdResponseParserVast;
import org.prebid.mobile.rendering.video.vast.Ad;
import org.prebid.mobile.rendering.video.vast.BaseId;
import org.prebid.mobile.rendering.video.vast.BaseValue;
import org.prebid.mobile.rendering.video.vast.Companion;
import org.prebid.mobile.rendering.video.vast.Creative;
import org.prebid.mobile.rendering.video.vast.Extension;
import org.prebid.mobile.rendering.video.vast.Extensions;
import org.prebid.mobile.rendering.video.vast.Icon;
import org.prebid.mobile.rendering.video.vast.InLine;
import org.prebid.mobile.rendering.video.vast.Linear;
import org.prebid.mobile.rendering.video.vast.MediaFile;
import org.prebid.mobile.rendering.video.vast.StaticResource;
import org.prebid.mobile.rendering.video.vast.VAST;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the public {@link VastAdInfo} from a parsed VAST chain — the outermost document and the
 * innermost one the wrappers led to.
 * <p>
 * The creative's own details (title, advertiser, description, media, companions, icons) come from the
 * innermost InLine ad: that is the creative that plays. The Genuin extension is looked for there
 * first, then in each wrapper from the innermost outwards, since an ad server may add it while
 * wrapping. Timing and the click-through are passed in as the SDK already resolved them for playback,
 * so the model and the player can never disagree.
 */
public final class VastAdInfoFactory {

    private VastAdInfoFactory() {
    }

    @NonNull
    public static VastAdInfo from(
        @NonNull AdResponseParserVast rootParser,
        @NonNull AdResponseParserVast latestParser,
        @Nullable String clickThroughUrl,
        long durationMs,
        long skipOffsetMs
    ) {
        VastAdInfo.Builder builder = new VastAdInfo.Builder()
            .setClickThroughUrl(clickThroughUrl)
            .setDurationMs(Math.max(durationMs, 0))
            .setSkipOffsetMs(skipOffsetMs >= 0 ? skipOffsetMs : VastAdInfo.NOT_SKIPPABLE);

        Ad inlineAd = firstInlineAd(latestParser.getVast());
        if (inlineAd != null) {
            InLine inline = inlineAd.getInline();
            builder.setAdId(inlineAd.getId())
                .setAdSystem(inline.getAdSystem() == null ? null : trimmed(inline.getAdSystem().getValue()))
                .setAdTitle(valueOf(inline.getAdTitle()))
                .setAdvertiser(valueOf(inline.getAdvertiser()))
                .setDescription(valueOf(inline.getDescription()));
            addCreatives(builder, inline.getCreatives());
        }

        Extension genuin = genuinExtension(rootParser);
        if (genuin != null) {
            builder.setCtaText(genuin.getCtaText())
                .setBrandLogoUrl(genuin.getBrandLogoUrl())
                .setBrandColor(genuin.getBrandColor());
        }
        return builder.build();
    }

    @Nullable
    private static Ad firstInlineAd(@Nullable VAST vast) {
        if (vast == null || vast.getAds() == null) {
            return null;
        }
        for (Ad ad : vast.getAds()) {
            if (ad.getInline() != null) {
                return ad;
            }
        }
        return null;
    }

    private static void addCreatives(VastAdInfo.Builder builder, @Nullable List<Creative> creatives) {
        if (creatives == null) {
            return;
        }
        for (Creative creative : creatives) {
            Linear linear = creative.getLinear();
            if (linear != null) {
                addMediaFiles(builder, linear.getMediaFiles());
                addIcons(builder, linear.getIcons());
            }
            addCompanions(builder, creative.getCompanionAds());
        }
    }

    private static void addMediaFiles(VastAdInfo.Builder builder, @Nullable List<MediaFile> mediaFiles) {
        if (mediaFiles == null) {
            return;
        }
        for (MediaFile mediaFile : mediaFiles) {
            String url = trimmed(mediaFile.getValue());
            if (url != null) {
                builder.addMediaFile(new VastAdInfo.MediaFile(
                    url,
                    trimmed(mediaFile.getType()),
                    trimmed(mediaFile.getDelivery()),
                    toInt(mediaFile.getWidth()),
                    toInt(mediaFile.getHeight()),
                    toInt(mediaFile.getBitrate())
                ));
            }
        }
    }

    private static void addIcons(VastAdInfo.Builder builder, @Nullable List<Icon> icons) {
        if (icons == null) {
            return;
        }
        for (Icon icon : icons) {
            String url = imageUrl(icon.getStaticResource());
            if (url != null) {
                String clickThrough = icon.getIconClicks() == null
                                      ? null
                                      : valueOf(icon.getIconClicks().getIconClickThrough());
                builder.addIcon(new VastAdInfo.Image(
                    url,
                    trimmed(icon.getStaticResource().getCreativeType()),
                    toInt(icon.getWidth()),
                    toInt(icon.getHeight()),
                    clickThrough,
                    trimmed(icon.getProgram())
                ));
            }
        }
    }

    private static void addCompanions(VastAdInfo.Builder builder, @Nullable List<Companion> companions) {
        if (companions == null) {
            return;
        }
        for (Companion companion : companions) {
            String url = imageUrl(companion.getStaticResource());
            if (url != null) {
                builder.addCompanion(new VastAdInfo.Image(
                    url,
                    trimmed(companion.getStaticResource().getCreativeType()),
                    toInt(companion.getWidth()),
                    toInt(companion.getHeight()),
                    valueOf(companion.getCompanionClickThrough()),
                    valueOf(companion.getAltText())
                ));
            }
        }
    }

    /**
     * The Genuin extension nearest the creative: the innermost document's InLine first, then each
     * wrapper outwards.
     */
    @Nullable
    private static Extension genuinExtension(@NonNull AdResponseParserVast rootParser) {
        List<AdResponseParserVast> chain = new ArrayList<>();
        for (AdResponseParserVast parser = rootParser; parser != null; parser = parser.getWrappedVASTXml()) {
            if (chain.contains(parser)) {
                break;
            }
            chain.add(parser);
        }
        for (int i = chain.size() - 1; i >= 0; i--) {
            Extension found = genuinExtension(chain.get(i).getVast());
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Nullable
    private static Extension genuinExtension(@Nullable VAST vast) {
        if (vast == null || vast.getAds() == null) {
            return null;
        }
        for (Ad ad : vast.getAds()) {
            Extensions extensions = ad.getInline() != null
                                    ? ad.getInline().getExtensions()
                                    : ad.getWrapper() != null ? ad.getWrapper().getExtensions() : null;
            if (extensions == null || extensions.getExtensions() == null) {
                continue;
            }
            for (Extension extension : extensions.getExtensions()) {
                if (extension.isGenuin()) {
                    return extension;
                }
            }
        }
        return null;
    }

    @Nullable
    private static String imageUrl(@Nullable StaticResource resource) {
        return resource == null ? null : trimmed(resource.getValue());
    }

    @Nullable
    private static String valueOf(@Nullable BaseValue value) {
        return value == null ? null : trimmed(value.getValue());
    }

    @Nullable
    private static String valueOf(@Nullable BaseId value) {
        return value == null ? null : trimmed(value.getValue());
    }

    @Nullable
    private static String trimmed(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static int toInt(@Nullable String value) {
        String number = trimmed(value);
        if (number == null) {
            return 0;
        }
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
