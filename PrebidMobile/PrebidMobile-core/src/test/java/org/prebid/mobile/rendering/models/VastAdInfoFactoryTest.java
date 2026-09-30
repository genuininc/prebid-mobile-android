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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.mobile.api.data.VastAdInfo;
import org.prebid.mobile.rendering.parser.AdResponseParserVast;
import org.prebid.mobile.test.utils.ResourceUtils;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class VastAdInfoFactoryTest {

    private static AdResponseParserVast parse(String resource) throws Exception {
        return new AdResponseParserVast(ResourceUtils.convertResourceToString(resource));
    }

    @Test
    public void inlineAd_exposesTheCreativesOwnDetails() throws Exception {
        AdResponseParserVast parser = parse("vast_inline_ad_info.xml");

        VastAdInfo info = VastAdInfoFactory.from(parser, parser, "https://acme.test/sale", 15_000, 5_000);

        assertEquals("ad-info-1", info.getAdId());
        assertEquals("Genuin Test Server", info.getAdSystem());
        assertEquals("Summer Sale", info.getAdTitle());
        assertEquals("Acme Shoes", info.getAdvertiser());
        assertEquals("Up to 50% off every running shoe.", info.getDescription());
        assertEquals("https://acme.test/sale", info.getClickThroughUrl());
        assertEquals(15_000, info.getDurationMs());
        assertEquals(5_000, info.getSkipOffsetMs());
    }

    @Test
    public void inlineAd_listsVideoAndAudioMediaFiles() throws Exception {
        AdResponseParserVast parser = parse("vast_inline_ad_info.xml");

        VastAdInfo info = VastAdInfoFactory.from(parser, parser, null, 15_000, -1);

        assertEquals(2, info.getMediaFiles().size());
        VastAdInfo.MediaFile video = info.getMediaFiles().get(0);
        assertEquals("https://cdn.test/portrait.mp4", video.getUrl());
        assertEquals("video/mp4", video.getType());
        assertEquals(720, video.getWidth());
        assertEquals(1280, video.getHeight());
        assertEquals(1200, video.getBitrateKbps());
        VastAdInfo.MediaFile audio = info.getMediaFiles().get(1);
        assertEquals("audio/mpeg", audio.getType());
        assertEquals(0, audio.getWidth());
        assertEquals(128, audio.getBitrateKbps());
    }

    @Test
    public void inlineAd_exposesStaticIconsAndCompanionsOnly() throws Exception {
        AdResponseParserVast parser = parse("vast_inline_ad_info.xml");

        VastAdInfo info = VastAdInfoFactory.from(parser, parser, null, 15_000, -1);

        assertEquals(1, info.getIcons().size());
        VastAdInfo.Image icon = info.getIcons().get(0);
        assertEquals("https://cdn.test/logo.png", icon.getUrl());
        assertEquals("image/png", icon.getCreativeType());
        assertEquals("BrandLogo", icon.getLabel());
        assertEquals("https://acme.test", icon.getClickThroughUrl());

        // The HTML-only companion has no image to show, so it is left out.
        assertEquals(1, info.getCompanions().size());
        VastAdInfo.Image companion = info.getCompanions().get(0);
        assertEquals("https://cdn.test/companion.jpg", companion.getUrl());
        assertEquals(300, companion.getWidth());
        assertEquals(250, companion.getHeight());
        assertEquals("Shop the sale", companion.getLabel());
        assertEquals("https://acme.test/companion", companion.getClickThroughUrl());
    }

    @Test
    public void genuinExtension_suppliesCtaTextAndBrand() throws Exception {
        AdResponseParserVast parser = parse("vast_inline_ad_info.xml");

        VastAdInfo info = VastAdInfoFactory.from(parser, parser, null, 15_000, -1);

        assertEquals("Shop now", info.getCtaText());
        assertEquals("https://cdn.test/brand.png", info.getBrandLogoUrl());
        assertEquals("#FF5A00", info.getBrandColor());
    }

    @Test
    public void genuinExtension_onTheInlineAdWinsOverTheWrappers() throws Exception {
        AdResponseParserVast wrapper = parse("vast_wrapper_genuin_extension.xml");
        AdResponseParserVast inline = parse("vast_inline_ad_info.xml");
        wrapper.setWrapper(inline);

        VastAdInfo info = VastAdInfoFactory.from(wrapper, inline, null, 15_000, -1);

        assertEquals("Shop now", info.getCtaText());
        assertEquals("#FF5A00", info.getBrandColor());
        // The creative's own details still come from the inline ad the wrapper led to.
        assertEquals("Summer Sale", info.getAdTitle());
    }

    @Test
    public void genuinExtension_fromAWrapperFillsAnInlineAdWithoutOne() throws Exception {
        AdResponseParserVast wrapper = parse("vast_wrapper_genuin_extension.xml");
        AdResponseParserVast inline = parse("vast_inline_minimal.xml");
        wrapper.setWrapper(inline);

        VastAdInfo info = VastAdInfoFactory.from(wrapper, inline, null, 10_000, -1);

        assertEquals("Install", info.getCtaText());
        assertEquals("#00AAFF", info.getBrandColor());
        assertNull(info.getBrandLogoUrl());
    }

    @Test
    public void minimalAd_leavesWhatItDoesNotSayEmpty() throws Exception {
        AdResponseParserVast parser = parse("vast_inline_minimal.xml");

        VastAdInfo info = VastAdInfoFactory.from(parser, parser, null, -1, -1);

        assertNull(info.getAdTitle());
        assertNull(info.getAdvertiser());
        assertNull(info.getDescription());
        assertNull(info.getClickThroughUrl());
        assertNull(info.getCtaText());
        assertEquals(0, info.getDurationMs());
        assertEquals(VastAdInfo.NOT_SKIPPABLE, info.getSkipOffsetMs());
        assertTrue(info.getIcons().isEmpty());
        assertTrue(info.getCompanions().isEmpty());
        assertEquals(1, info.getMediaFiles().size());
    }
}
