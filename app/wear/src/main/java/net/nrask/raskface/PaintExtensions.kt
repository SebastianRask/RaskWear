package net.nrask.raskface

import android.graphics.Paint
import android.graphics.Rect

/**
 * Created by Sebastian Rask Jepsen on 03/01/2018.
 */

fun Paint.getHeightForText(text: String): Float {
    val textBounds = Rect()
    getTextBounds(text, 0, text.length, textBounds)

    return textBounds.height() - descent()
}