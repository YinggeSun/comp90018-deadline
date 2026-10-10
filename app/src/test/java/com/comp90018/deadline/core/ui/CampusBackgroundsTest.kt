package com.comp90018.deadline.core.ui

import com.comp90018.deadline.domain.level.model.SemesterLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CampusBackgroundsTest {
    @Test
    fun everyLevelHasItsOwnPhoto() {
        val photos = SemesterLevel.ALL.map { CampusBackgrounds.forLevel(it.id) }

        photos.forEach { assertNotNull(it) }
        assertEquals("Each level needs a different photo", photos.size, photos.toSet().size)
    }

    @Test
    fun homeHasAPhotoOfItsOwn() {
        val levelPhotos = SemesterLevel.ALL.map { CampusBackgrounds.forLevel(it.id) }

        assertFalse(CampusBackgrounds.home in levelPhotos)
    }

    @Test
    fun unknownLevelHasNoPhoto() {
        assertNull(CampusBackgrounds.forLevel("level_7"))
        assertNull(CampusBackgrounds.forLevel("sample_level"))
    }
}
