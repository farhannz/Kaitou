package com.farhannz.kaitou.ui.components.utils

import com.farhannz.kaitou.data.models.GroupedResult
import com.farhannz.kaitou.data.models.DetectionResult
import org.opencv.core.Point
import com.farhannz.kaitou.domain.GroupedResult as DomainGroupedResult

// TODO(TEMPORARY CONVERTERS BETWEEN CURRENT IMPLEMENTATION AND DOMAIN TYPE)


// TODO(temp)
fun GroupedResult.toDomain(): DomainGroupedResult {
    val result = this
    val detections = com.farhannz.kaitou.domain.DetectionResult(
        result.detections.boxes.map { box ->
            box.map {
                com.farhannz.kaitou.domain.Point(it.x.toFloat(), it.y.toFloat())
            }
        },
        result.detections.scores.map { it.toFloat() }
    )

    val group = DomainGroupedResult(
        detections,
        result.grouped.map { group ->
            com.farhannz.kaitou.domain.Group(
                group.first.map {
                    com.farhannz.kaitou.domain.Point(it.x.toFloat(), it.y.toFloat())
                },
                group.second
            )
        }
    )
    return group
}


// TODO(temp)
fun DomainGroupedResult.toCurrentImpl(): GroupedResult {
    val result = this
    val detections = DetectionResult(
        result.detections.boxes.map { box ->
            box.map {
                Point(it.x.toDouble(), it.y.toDouble())
            }
        },
        result.detections.scores.map { it.toDouble() }
    )

    val group = GroupedResult(
        detections,
        result.grouped.map { group ->
            Pair(
                group.region.map {
                    Point(it.x.toDouble(), it.y.toDouble())
                },
                group.memberBoxIndices
            )
        }
    )
    return group
}


