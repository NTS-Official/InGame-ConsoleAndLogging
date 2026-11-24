package com.nts.ingamecnl.client.gui

internal data class FollowHint(val x: Int, val y: Int, val width: Int, val height: Int) {
    fun contains(mouseX: Double, mouseY: Double): Boolean =
        mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height
}
