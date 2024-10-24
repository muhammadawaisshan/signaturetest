package com.iobits.tech.pdfsign.utils

import kotlin.math.pow
import kotlin.math.sqrt


/**
 * Created by Zahid.Ali on 3/24/2015.
 */
class Point(val x: Float, val y: Float, val time: Long) {
    private fun distanceTo(start: Point): Float {
        return (sqrt((x - start.x).pow(2.0f) + (y - start.y).pow(2.0f)))
    }

    fun velocityFrom(start: Point): Float {
        return distanceTo(start) / (this.time - start.time)
    }
}