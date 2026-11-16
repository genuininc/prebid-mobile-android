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

/**
 * Rewrites a VAST wrapper's {@code VASTAdTagURI} before the Rendering API fetches it.
 * <p>
 * A wrapper bid points at another ad server's tag, which the device fetches. Some of that tag's
 * parameters are known only to the app playing the ad, never to Prebid Server: for a Google Ad
 * Manager tag, the Google PAL nonce ({@code givn}) and whether the ad autoplays and starts muted.
 * The decorator adds them. It is called on every wrapper hop, and returns the URL unchanged for a
 * tag it does not handle.
 *
 * @see PrebidMobile#setVastWrapperUrlDecorator(VastWrapperUrlDecorator)
 */
public interface VastWrapperUrlDecorator {

    /**
     * @param vastUrl the wrapper's tag URL, as the VAST names it
     * @return the URL to fetch instead; an empty result fetches {@code vastUrl} unchanged
     */
    @NonNull
    String decorate(@NonNull String vastUrl);
}
