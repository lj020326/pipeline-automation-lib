/*-
 * #%L
 * dcapi.dettonville.org
 * %%
 * Copyright (C) 2019 Dettonville API
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.dettonville.api.pipeline.utils

// ref: https://gist.github.com/robhruska/4612278
class MapMerge {

    /**
     * Deeply merges the contents of each Map in sources, merging from
     * "right to left" and returning the merged Map.
     *
     * Mimics 'extend()' functions often seen in JavaScript libraries.
     * Any specific Map implementations (e.g. TreeMap, LinkedHashMap)
     * are not guaranteed to be retained. The ordering of the keys in
     * the result Map is not guaranteed. Only nested maps will be
     * merged; primitives, objects, and other collection types will be
     * overwritten.
     *
     * The source maps will not be modified.
     */
    static Map merge(Map[] sources) {
        if (sources.length == 0) return [:]
        if (sources.length == 1) return sources[0]

        sources.inject([:]) { result, source ->
            source.each { k, v ->
//                result[k] = result[k] instanceof Map ? merge(result[k], v) : v

                if (result[k] instanceof Map) {
                    result[k] = merge(result[k], v)
                } else if (result[k] instanceof List) {
                    // ref: https://stackoverflow.com/questions/45791945/how-to-join-list-of-maps-in-groovy
//                     result[k] = (result[k] + v).groupBy { it.name }.collect{ it.value }.collect{ item -> Map m = [:] ; item.collect{ m +=it }; m }
                    if (v[0] instanceof Map) {
                        result[k] = (result[k] + v).groupBy {
                                (it instanceof Map) ? (it['name'] ?: it['id'] ?: it['key'] ?: it.keySet()[0]) : it
                            }.collect{ it.value }.collect{ item -> Map m = [:] ; item.collect{ m +=it }; m }
                    } else {
                        result[k] = (result[k] + v)
                    }
                } else {
                    result[k] = v
                }
            }
            result
        }
    }
}
