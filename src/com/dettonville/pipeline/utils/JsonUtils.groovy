package com.dettonville.pipeline.utils

import com.dettonville.pipeline.utils.JsonUtils

import groovy.json.*

//import groovy.json.JsonSlurper
//import net.sf.json.JSON

class JsonUtilsException extends Exception {
    JsonUtilsException(String message) {
        super(message)
    }
}

// ref: https://gist.github.com/tadaedo/c6394e0d34abf7cf6cf3
class JsonUtils implements Serializable {
    private static final long serialVersionUID = 1L

//    DSL dsl
    def dsl

    /**
     * @param dsl The DSL object of the current pipeline script (available via this.steps in pipeline scripts)
     */
//    JsonUtils(DSL dsl) {
    JsonUtils(def dsl) {
        this.dsl = dsl
    }

//     static String printToJsonString(def config) {
    static String printToJsonString(Object config) {
//         if (config instanceof List) {
//             return config.toString()
//         }
        try {
            if (config) {
                return JsonOutput.prettyPrint(JsonOutput.toJson(config))
            } else {
                return ""
            }
        } catch (Exception err) {
            throw new JsonUtilsException(JsonUtils.class.getName() + ".printToJsonString(): exception occurred ==> [${err}]")
        }
    }

    List getJsonDiffs(String json1, String json2, Boolean strict = false) {
        return getJsonDiffs('root', json1, json2, strict)
    }

    List getJsonDiffs(Map map1, Map map2, Boolean strict = false) {
        return getJsonDiffs('root', map1, map2, strict)
    }

    Map getJsonDiffMap(Map map1, Map map2, Boolean strict = false) {
        List diffs = getJsonDiffs(map1, map2, strict)
        Map diffMap = diffs.collectEntries { Map diff ->
            [(diff.label): diff.findAll { it.key != 'label' }]
        }
        return diffMap
    }

//    @NonCPS
    List getJsonDiffs(String label, String json1, String json2, Boolean strict = false) {

//        JsonSlurper sl = new JsonSlurper()
//        def obj1 = sl.parseText(json1)
//        def obj2 = sl.parseText(json2)
        def obj1 = dsl.readJSON text: json1
        def obj2 = dsl.readJSON text: json2

//        println("obj1=${printToJsonString(obj1)}")
//        println("obj2=${printToJsonString(obj2)}")

        return getJsonDiffValue(label, obj1, obj2, strict)
    }

    List getJsonDiffs(String label, Map map1, Map map2, Boolean strict = false) {
        return getJsonDiffValue(label, map1, map2, strict)
    }

    List getJsonDiffValue(String label, def val1, def val2, Boolean strict = false) {
        List results = []
        if (val1 instanceof Map && val2 instanceof Map) {
            return getJsonDiffMap(label, val1, val2, strict)
        } else if (val1 instanceof List && val2 instanceof List) {
            return getJsonDiffList(label, val1, val2, strict)
        } else if (val1.toString() != val2.toString()) {
            Map diff = [:]
            diff.label = label
//            diff.value = "'${val1}' - '${val2}'"
            diff.value1 = "${val1}"
            diff.value2 = "${val2}"
            diff.type = 'value mismatch'
            results.add(diff)
        } else if (strict && val1.getClass() != val2.getClass()) {
            Map diff = [:]
            diff.label = label
//            diff.value = "${val1.getClass()} - ${val2.getClass()}"
            diff.value1 = "${val1.getClass()}"
            diff.value2 = "${val2.getClass()}"
            diff.type = 'type mismatch'
            results.add(diff)
        }
        return results
    }

    List getJsonDiffMap(String label, Map map1, Map map2, Boolean strict = false) {
        List results = []
        map1.entrySet().each { e1 ->
            if (!map2.containsKey(e1.key)) {
                Map diff = [:]
                diff.label = label + "." + e1.key
//                diff.value = ""
                diff.value1 = ""
                diff.value2 = ""
                diff.type = 'no key json2'
                results.add(diff)
            } else {
                results.addAll(getJsonDiffValue("${label}.${e1.key}", e1.value, map2.get(e1.key), strict))
            }
        }
        // map2
        map2.entrySet().each { e2 ->
            if (!map1.containsKey(e2.key)) {
                Map diff = [:]
                diff.label = label + "." + e2.key
//                diff.value = ""
                diff.value1 = ""
                diff.value2 = ""
                diff.type = 'no key json1'
                results.add(diff)
            }
        }
        println("finished: results = ${results}")
        return results
    }

    List getJsonDiffList(String label, List list1, List list2, Boolean strict = false) {
        List results = []
        if (list1.size() != list2.size()) {
            Map diff = [:]
            diff.label = label
//            diff.value = "'${list1.size()}' - '${list2.size()}'"
            diff.value1 = "list.size() = ${list1.size()}"
            diff.value2 = "list.size() = ${list2.size()}"
            diff.type = 'list length mismatch'
            results.add(diff)
        } else {
            (0..<list1.size()).each {
                results.addAll(getJsonDiffValue("${label}[${it}]", list1.get(it), list2.get(it), strict))
            }
        }
        return results
    }


    ///////////
    // isJsonDiff
    ///////////
    Boolean isJsonDiff(String json1, String json2, Boolean strict = false) {
        return isJsonDiff('root', json1, json2, strict)
    }

    Boolean isJsonDiff(Map json1, Map json2, Boolean strict = false) {
        return isJsonDiff('root', json1, json2, strict)
    }

//    @NonCPS
    Boolean isJsonDiff(String label, String json1, String json2, Boolean strict = false) {
//        JsonSlurper sl = new JsonSlurper()
//        def obj1 = sl.parseText(json1)
//        def obj2 = sl.parseText(json2)
        def obj1 = dsl.readJSON text: json1
        def obj2 = dsl.readJSON text: json2

//        println("obj1=${printToJsonString(obj1)}")
//        println("obj2=${printToJsonString(obj2)}")

        return checkValue(label, obj1, obj2, strict)
    }

    Boolean isJsonDiff(String label, Map map1, Map map2, Boolean strict = false) {
        return checkValue(label, map1, map2, strict)
    }

    Boolean checkValue(String label, def val1, def val2, Boolean strict = false) {
        if (val1 instanceof Map && val2 instanceof Map) {
            return checkMap(label, val1, val2, strict)
        } else if (val1 instanceof List && val2 instanceof List) {
            return checkList(label, val1, val2, strict)
        } else if (val1.toString() != val2.toString()) {
            putAlert label, "'${val1}' - '${val2}'", 'value mismatch'
            return true
        } else if (strict && val1.getClass() != val2.getClass()) {
            putAlert label, "${val1.getClass()} - ${val2.getClass()}", 'type mismatch'
            return true
        }
        return false
    }

    Boolean checkMap(String label, Map map1, Map map2, Boolean strict = false) {
        List results = []
        map1.entrySet().each { e1 ->
            if (!map2.containsKey(e1.key)) {
                putAlert label + "." + e1.key, '', 'no key json2'
                results.add(true)
            } else {
                results.add(checkValue(label + '.' + e1.key, e1.value, map2.get(e1.key), strict))
            }
        }
        // map2
        map2.entrySet().each { e2 ->
            if (!map1.containsKey(e2.key)) {
                putAlert label + "." + e2.key, '', 'no key json1'
                results.add(true)
            }
        }
        Boolean result = (results.size()>0) ? results.inject { a, b -> a || b } : false
//        println("finished: result = ${result}")
        return result
    }

    Boolean checkList(String label, List list1, List list2, Boolean strict = false) {
        List results = []
        if (list1.size() != list2.size()) {
            putAlert label, "'${list1.size()}' - '${list2.size()}'", 'list length mismatch'
            results.add(true)
        } else {
            (0..<list1.size()).each {
                results.add(checkValue("${label}[${it}]", list1.get(it), list2.get(it), strict))
            }
        }
        Boolean result = (results.size()>0) ? results.inject { a, b -> a || b } : false
//        println("finished: result = ${result}")
        return result
    }

    void putAlert(key, values, comment) {
        println("${key} : ${values} // ${comment}")
//        dsl.println "${logPrefix} ${key} : ${values} // ${comment}"
    }
}
