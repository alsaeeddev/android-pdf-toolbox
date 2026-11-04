package com.example.feature_editor.domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.feature_editor.model.Annotation
import com.example.feature_editor.model.EditorFontFamily
import com.example.feature_editor.model.InkAnnotation
import com.example.feature_editor.model.RedactionAnnotation
import com.example.feature_editor.model.ShapeAnnotation
import com.example.feature_editor.model.ShapeType
import com.example.feature_editor.model.SignatureAnnotation
import com.example.feature_editor.model.StampAnnotation
import com.example.feature_editor.model.StampType
import com.example.feature_editor.model.TextAlignment
import com.example.feature_editor.model.TextAnnotation
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object AnnotationJsonSerializer {

    fun toJson(annotations: List<Annotation>): String {
        val array = JSONArray()
        for (ann in annotations) {
            val obj = JSONObject()
            obj.put("id", ann.id)
            obj.put("pageIndex", ann.pageIndex)
            obj.put("rotation", ann.rotation.toDouble())
            obj.put("zIndex", ann.zIndex)

            when (ann) {
                is TextAnnotation -> {
                    obj.put("type", "TEXT")
                    obj.put("text", ann.text)
                    obj.put("posX", ann.position.x.toDouble())
                    obj.put("posY", ann.position.y.toDouble())
                    obj.put("color", ann.color.toArgb())
                    obj.put("fontSize", ann.fontSize.toDouble())
                    obj.put("isBold", ann.isBold)
                    obj.put("isItalic", ann.isItalic)
                    obj.put("alignment", ann.alignment.name)
                    obj.put("fontFamily", ann.fontFamily.name)
                    obj.put("width", ann.width.toDouble())
                    obj.put("height", ann.height.toDouble())
                }
                is ShapeAnnotation -> {
                    obj.put("type", "SHAPE")
                    obj.put("shapeType", ann.shapeType.name)
                    obj.put("startX", ann.start.x.toDouble())
                    obj.put("startY", ann.start.y.toDouble())
                    obj.put("endX", ann.end.x.toDouble())
                    obj.put("endY", ann.end.y.toDouble())
                    obj.put("color", ann.color.toArgb())
                    obj.put("strokeWidth", ann.strokeWidth.toDouble())
                    ann.fillColor?.let { obj.put("fillColor", it.toArgb()) }
                    obj.put("opacity", ann.opacity.toDouble())
                }
                is InkAnnotation -> {
                    obj.put("type", "INK")
                    val ptsArr = JSONArray()
                    for (p in ann.points) {
                        val ptObj = JSONObject()
                        ptObj.put("x", p.x.toDouble())
                        ptObj.put("y", p.y.toDouble())
                        ptsArr.put(ptObj)
                    }
                    obj.put("points", ptsArr)
                    obj.put("color", ann.color.toArgb())
                    obj.put("strokeWidth", ann.strokeWidth.toDouble())
                    obj.put("isHighlighter", ann.isHighlighter)
                    obj.put("opacity", ann.opacity.toDouble())
                }
                is StampAnnotation -> {
                    obj.put("type", "STAMP")
                    obj.put("stampType", ann.stampType.name)
                    obj.put("posX", ann.position.x.toDouble())
                    obj.put("posY", ann.position.y.toDouble())
                    obj.put("dateText", ann.dateText)
                    obj.put("scale", ann.scale.toDouble())
                }
                is SignatureAnnotation -> {
                    obj.put("type", "SIGNATURE")
                    obj.put("posX", ann.position.x.toDouble())
                    obj.put("posY", ann.position.y.toDouble())
                    obj.put("strokeColor", ann.strokeColor.toArgb())
                    obj.put("scale", ann.scale.toDouble())
                    obj.put("filled", ann.filled)
                    val strokesArr = JSONArray()
                    for (stroke in ann.strokes) {
                        val sArr = JSONArray()
                        for (p in stroke) {
                            val ptObj = JSONObject()
                            ptObj.put("x", p.x.toDouble())
                            ptObj.put("y", p.y.toDouble())
                            sArr.put(ptObj)
                        }
                        strokesArr.put(sArr)
                    }
                    obj.put("strokes", strokesArr)
                }
                is RedactionAnnotation -> {
                    obj.put("type", "REDACTION")
                    val boundsObj = JSONObject()
                    boundsObj.put("left", ann.bounds.left.toDouble())
                    boundsObj.put("top", ann.bounds.top.toDouble())
                    boundsObj.put("right", ann.bounds.right.toDouble())
                    boundsObj.put("bottom", ann.bounds.bottom.toDouble())
                    obj.put("bounds", boundsObj)
                }
                else -> {}
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun fromJson(jsonStr: String): List<Annotation> {
        val list = mutableListOf<Annotation>()
        if (jsonStr.isBlank()) return list
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val type = obj.optString("type")
            val id = obj.optString("id", UUID.randomUUID().toString())
            val pageIndex = obj.optInt("pageIndex", 0)
            val rotation = obj.optDouble("rotation", 0.0).toFloat()
            val zIndex = obj.optInt("zIndex", 0)

            when (type) {
                "TEXT" -> {
                    list.add(
                        TextAnnotation(
                            annotationId = id,
                            page = pageIndex,
                            text = obj.optString("text", ""),
                            position = Offset(obj.optDouble("posX", 0.0).toFloat(), obj.optDouble("posY", 0.0).toFloat()),
                            color = Color(obj.optInt("color", Color.Black.toArgb())),
                            fontSize = obj.optDouble("fontSize", 20.0).toFloat(),
                            isBold = obj.optBoolean("isBold", false),
                            isItalic = obj.optBoolean("isItalic", false),
                            alignment = try { TextAlignment.valueOf(obj.optString("alignment", "LEFT")) } catch (_: Exception) { TextAlignment.LEFT },
                            fontFamily = try { EditorFontFamily.valueOf(obj.optString("fontFamily", "SANS_SERIF")) } catch (_: Exception) { EditorFontFamily.SANS_SERIF },
                            rotation = rotation,
                            width = obj.optDouble("width", 240.0).toFloat(),
                            height = obj.optDouble("height", 48.0).toFloat(),
                            zIndex = zIndex
                        )
                    )
                }
                "SHAPE" -> {
                    val fillInt = if (obj.has("fillColor")) obj.getInt("fillColor") else null
                    list.add(
                        ShapeAnnotation(
                            annotationId = id,
                            page = pageIndex,
                            shapeType = try { ShapeType.valueOf(obj.optString("shapeType", "RECTANGLE")) } catch (_: Exception) { ShapeType.RECTANGLE },
                            start = Offset(obj.optDouble("startX", 0.0).toFloat(), obj.optDouble("startY", 0.0).toFloat()),
                            end = Offset(obj.optDouble("endX", 0.0).toFloat(), obj.optDouble("endY", 0.0).toFloat()),
                            color = Color(obj.optInt("color", Color.Red.toArgb())),
                            strokeWidth = obj.optDouble("strokeWidth", 4.0).toFloat(),
                            fillColor = fillInt?.let { Color(it) },
                            opacity = obj.optDouble("opacity", 1.0).toFloat(),
                            rotation = rotation,
                            zIndex = zIndex
                        )
                    )
                }
                "INK" -> {
                    val ptsArr = obj.optJSONArray("points")
                    val pts = mutableListOf<Offset>()
                    if (ptsArr != null) {
                        for (pIdx in 0 until ptsArr.length()) {
                            val ptObj = ptsArr.getJSONObject(pIdx)
                            pts.add(Offset(ptObj.optDouble("x", 0.0).toFloat(), ptObj.optDouble("y", 0.0).toFloat()))
                        }
                    }
                    list.add(
                        InkAnnotation(
                            annotationId = id,
                            page = pageIndex,
                            points = pts,
                            color = Color(obj.optInt("color", Color.Black.toArgb())),
                            strokeWidth = obj.optDouble("strokeWidth", 4.0).toFloat(),
                            isHighlighter = obj.optBoolean("isHighlighter", false),
                            opacity = obj.optDouble("opacity", 1.0).toFloat(),
                            rotation = rotation,
                            zIndex = zIndex
                        )
                    )
                }
                "STAMP" -> {
                    list.add(
                        StampAnnotation(
                            annotationId = id,
                            page = pageIndex,
                            stampType = try { StampType.valueOf(obj.optString("stampType", "APPROVED")) } catch (_: Exception) { StampType.APPROVED },
                            position = Offset(obj.optDouble("posX", 0.0).toFloat(), obj.optDouble("posY", 0.0).toFloat()),
                            dateText = obj.optString("dateText", ""),
                            scale = obj.optDouble("scale", 1.0).toFloat(),
                            rotation = rotation,
                            zIndex = zIndex
                        )
                    )
                }
                "SIGNATURE" -> {
                    val strokesArr = obj.optJSONArray("strokes")
                    val strokes = mutableListOf<List<Offset>>()
                    if (strokesArr != null) {
                        for (sIdx in 0 until strokesArr.length()) {
                            val sArr = strokesArr.getJSONArray(sIdx)
                            val strokePts = mutableListOf<Offset>()
                            for (pIdx in 0 until sArr.length()) {
                                val ptObj = sArr.getJSONObject(pIdx)
                                strokePts.add(Offset(ptObj.optDouble("x", 0.0).toFloat(), ptObj.optDouble("y", 0.0).toFloat()))
                            }
                            strokes.add(strokePts)
                        }
                    }
                    list.add(
                        SignatureAnnotation(
                            annotationId = id,
                            page = pageIndex,
                            position = Offset(obj.optDouble("posX", 0.0).toFloat(), obj.optDouble("posY", 0.0).toFloat()),
                            strokes = strokes,
                            strokeColor = Color(obj.optInt("strokeColor", Color.Black.toArgb())),
                            scale = obj.optDouble("scale", 1.0).toFloat(),
                            filled = obj.optBoolean("filled", false),
                            rotation = rotation,
                            zIndex = zIndex
                        )
                    )
                }
                "REDACTION" -> {
                    val boundsObj = obj.optJSONObject("bounds")
                    val rect = if (boundsObj != null) {
                        Rect(
                            boundsObj.optDouble("left", 0.0).toFloat(),
                            boundsObj.optDouble("top", 0.0).toFloat(),
                            boundsObj.optDouble("right", 0.0).toFloat(),
                            boundsObj.optDouble("bottom", 0.0).toFloat()
                        )
                    } else Rect.Zero
                    list.add(
                        RedactionAnnotation(
                            annotationId = id,
                            page = pageIndex,
                            bounds = rect,
                            rotation = rotation,
                            zIndex = zIndex
                        )
                    )
                }
            }
        }
        return list
    }
}
