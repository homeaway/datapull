/* Copyright (c) 2019 Expedia Group.
 * All rights reserved.  http://www.homeaway.com

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 *      http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.homeaway.datapullclient.input;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Arrays;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Migration {
    @JsonProperty("source")
    private Source source;

    @JsonProperty("sources")
    private Source [] sources;

    @JsonProperty("destination")
    private Destination destination;

    public boolean isSingleSource(){
        return source != null ?  true : false;
    }

    @Override
    public String toString() {
        return "Migration{" + (isSingleSource() ? "source = "+source : "sources = "+Arrays.toString(sources))+
                ", destination=" + destination +
                '}';
    }
}
