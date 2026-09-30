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

package org.prebid.mobile.rendering.models.openrtb.bidRequests.imps;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.prebid.mobile.rendering.models.openrtb.bidRequests.BaseBid;

/** The OpenRTB 2.x {@code imp.audio} object. */
public class Audio extends BaseBid {

    public String[] mimes = null;

    public Integer minduration = null;
    public Integer maxduration = null;

    public int[] protocols;
    public Integer startdelay = null;

    public Integer minbitrate = null;
    public Integer maxbitrate = null;

    public int[] delivery;
    public int[] api;
    public int[] battr;

    public JSONObject getJsonObject() throws JSONException {
        JSONObject jsonObject = new JSONObject();

        if (mimes != null) {
            JSONArray jsonArray = new JSONArray();
            for (String mime : mimes) {
                jsonArray.put(mime);
            }
            toJSON(jsonObject, "mimes", jsonArray);
        }

        toJSON(jsonObject, "minduration", minduration);
        toJSON(jsonObject, "maxduration", maxduration);
        toJSON(jsonObject, "protocols", toJsonArray(protocols));
        toJSON(jsonObject, "startdelay", startdelay);
        toJSON(jsonObject, "minbitrate", minbitrate);
        toJSON(jsonObject, "maxbitrate", maxbitrate);
        toJSON(jsonObject, "delivery", toJsonArray(delivery));
        toJSON(jsonObject, "api", toJsonArray(api));
        toJSON(jsonObject, "battr", toJsonArray(battr));

        return jsonObject;
    }

    private static JSONArray toJsonArray(int[] values) {
        if (values == null) {
            return null;
        }
        JSONArray jsonArray = new JSONArray();
        for (int value : values) {
            jsonArray.put(value);
        }
        return jsonArray;
    }
}
