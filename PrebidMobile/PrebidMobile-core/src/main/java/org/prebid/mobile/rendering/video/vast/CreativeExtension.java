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

package org.prebid.mobile.rendering.video.vast;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * One {@code <CreativeExtension>}: its text, or the text inside the elements it holds. A creative
 * extension may hold elements of its own — Google Ad Manager puts a {@code <UniversalAdId>} in one —
 * so it is read through to its own end tag rather than as plain text, which would fail the whole
 * VAST on the first nested element.
 */
public class CreativeExtension extends VASTParserBase
{
    private final String value;

	public CreativeExtension(XmlPullParser p) throws XmlPullParserException, IOException
	{
		StringBuilder text = new StringBuilder();
		int depth = 1;
		while (depth > 0) {
			switch (p.next()) {
				case XmlPullParser.START_TAG:
					depth++;
					break;
				case XmlPullParser.END_TAG:
					depth--;
					break;
				case XmlPullParser.TEXT:
				case XmlPullParser.CDSECT:
					text.append(p.getText());
					break;
				case XmlPullParser.END_DOCUMENT:
					throw new XmlPullParserException("CreativeExtension is not closed", p, null);
				default:
					break;
			}
		}
		value = text.toString().trim();
	}

	public String getValue() {
		return value;
	}
}
