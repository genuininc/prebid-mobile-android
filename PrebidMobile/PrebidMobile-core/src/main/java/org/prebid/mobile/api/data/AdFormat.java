package org.prebid.mobile.api.data;

import androidx.annotation.Nullable;

import java.util.EnumSet;

/**
 * Internal ad format. Must be set up only inside the SDK.
 */
public enum AdFormat {
    BANNER,
    INTERSTITIAL,
    NATIVE,
    VAST,
    /** An audio imp. Its winning VAST is rendered by the same pipeline as {@link #VAST}. */
    AUDIO;

    public static EnumSet<AdFormat> fromSet(
            EnumSet<AdUnitFormat> adUnitFormats,
            boolean isInterstitial
    ) {
        if (adUnitFormats == null || adUnitFormats.isEmpty())
            throw new NullPointerException("List of ad unit formats must contain at least one item.");

        EnumSet<AdFormat> result = EnumSet.noneOf(AdFormat.class);
        for (AdUnitFormat format : adUnitFormats) {
            if (format == AdUnitFormat.BANNER) {
                if (isInterstitial) {
                    result.add(AdFormat.INTERSTITIAL);
                } else {
                    result.add(AdFormat.BANNER);
                }
            }
            if (format == AdUnitFormat.VIDEO) {
                result.add(AdFormat.VAST);
            }
            if (format == AdUnitFormat.AUDIO) {
                result.add(AdFormat.AUDIO);
            }
        }
        return result;
    }

    /**
     * Maps internal ad formats back to the public {@link AdUnitFormat} values.
     * {@link #BANNER} and {@link #INTERSTITIAL} both map to {@link AdUnitFormat#BANNER},
     * {@link #VAST} maps to {@link AdUnitFormat#VIDEO}, {@link #AUDIO} to {@link AdUnitFormat#AUDIO}. {@link #NATIVE} has no public
     * counterpart and is skipped.
     */
    public static EnumSet<AdUnitFormat> toSet(@Nullable EnumSet<AdFormat> adFormats) {
        EnumSet<AdUnitFormat> result = EnumSet.noneOf(AdUnitFormat.class);
        if (adFormats == null) return result;

        for (AdFormat format : adFormats) {
            if (format == AdFormat.BANNER || format == AdFormat.INTERSTITIAL) {
                result.add(AdUnitFormat.BANNER);
            }
            if (format == AdFormat.VAST) {
                result.add(AdUnitFormat.VIDEO);
            }
            if (format == AdFormat.AUDIO) {
                result.add(AdUnitFormat.AUDIO);
            }
        }
        return result;
    }
}