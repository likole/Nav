/*
 * Copyright (c) 2017 Baidu, Inc. All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.baidu.duer.dcs.http.callback;

import java.io.InputStream;

import okhttp3.Response;

/**
 * 数据流回调
 * <p>
 * Created by zhangyan42@baidu.com on 2017/6/2.
 */
public abstract class StreamDcsCallback extends DcsCallback<InputStream> {
    @Override
    public InputStream parseNetworkResponse(Response response, int id) throws Exception {
        return response.body().byteStream();
    }
}