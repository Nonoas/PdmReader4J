package com.example.pdmreader.util

import org.w3c.dom.Element
import org.w3c.dom.Node

fun Element.directChildElements(localName: String? = null): List<Element> {
    val elements = mutableListOf<Element>()
    val targetName = localName?.trim()
    val childNodes = childNodes
    for (index in 0 until childNodes.length) {
        val child = childNodes.item(index)
        if (child.nodeType != Node.ELEMENT_NODE) {
            continue
        }
        val element = child as Element
        if (targetName == null || element.localName == targetName) {
            elements += element
        }
    }
    return elements
}

fun Element.firstDirectChildElement(localName: String): Element? =
    directChildElements(localName).firstOrNull()

fun Element.firstDirectChildText(localName: String): String? =
    firstDirectChildElement(localName)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

fun Element.firstDescendant(localName: String): Element? {
    if (this.localName == localName) {
        return this
    }

    for (child in directChildElements()) {
        val match = child.firstDescendant(localName)
        if (match != null) {
            return match
        }
    }
    return null
}
