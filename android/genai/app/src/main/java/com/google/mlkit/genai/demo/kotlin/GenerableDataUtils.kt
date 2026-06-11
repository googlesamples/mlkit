package com.google.mlkit.genai.demo.kotlin

import android.util.Log
import com.google.mlkit.genai.structuredoutput.guided.GenerableDetail
import com.google.mlkit.genai.structuredoutput.guided.GenerableProvider
import java.util.ServiceLoader
import kotlin.reflect.KClass
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object GenerableDataUtils {
  private const val TAG = "GenerableDataUtils"
  private val generableProviders: Map<KClass<*>, GenerableProvider> by lazy {
    ServiceLoader.load(GenerableProvider::class.java, GenerableProvider::class.java.classLoader)
      .associateBy { it.targetClass }
  }

  fun getJsonSchema(outputClass: KClass<*>): String? {
    val provider = generableProviders[outputClass]
    if (provider == null) {
      Log.e(TAG, "GenerableProvider not found for class: ${outputClass.simpleName}")
      return null
    }

    val detail = provider.getGenerableDetail()
    try {
      return generableDetailToJson(detail).toString(2)
    } catch (e: JSONException) {
      Log.e(TAG, "Error generating JSON schema for class: ${outputClass.simpleName}", e)
      return null
    }
  }

  private fun generableDetailToJson(detail: GenerableDetail<*>): JSONObject {
    val result = JSONObject()
    result.put("description", detail.description)
    val properties = JSONObject()
    for (guide in detail.guideDetails) {
      val nestedProvider = generableProviders[guide.type]
      val listProvider = if (guide.isList) generableProviders[guide.listItemType] else null
      when {
        listProvider != null -> {
          properties.put(
            guide.name,
            JSONArray().put(generableDetailToJson(listProvider.getGenerableDetail())),
          )
        }
        nestedProvider != null -> {
          properties.put(guide.name, generableDetailToJson(nestedProvider.getGenerableDetail()))
        }
        else -> {
          val prop = JSONObject()
          prop.put(
            "type",
            if (guide.isList) "List<${guide.listItemType?.simpleName}>" else guide.type.simpleName,
          )
          if (guide.nullable) prop.put("nullable", true)
          guide.description?.let { prop.put("description", it) }
          guide.minItems?.let { prop.put("minItems", it) }
          guide.maxItems?.let { prop.put("maxItems", it) }
          guide.minimum?.let { prop.put("minimum", it) }
          guide.maximum?.let { prop.put("maximum", it) }
          guide.enumValues?.let { prop.put("enumValues", JSONArray(it.toList())) }
          properties.put(guide.name, prop)
        }
      }
    }
    result.put("properties", properties)
    return result
  }
}
