package com.khizrim.dialer

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import com.khizrim.dialer.data.MockData
import com.khizrim.dialer.model.CommMethod
import com.khizrim.dialer.model.Contact

/** Состояние прототипа целиком в памяти, без реальной телефонии. */
class DialerViewModel : ViewModel() {
    val contacts = mutableStateListOf<Contact>().apply { addAll(MockData.contacts) }
    val recents = MockData.recents

    fun contact(id: Long): Contact? = contacts.firstOrNull { it.id == id }

    fun setDefaultMethod(contactId: Long, method: CommMethod?) = update(contactId) {
        it.copy(defaultMethod = method)
    }

    fun toggleFavorite(contactId: Long) = update(contactId) { it.copy(favorite = !it.favorite) }

    private fun update(id: Long, transform: (Contact) -> Contact) {
        val index = contacts.indexOfFirst { it.id == id }
        if (index >= 0) contacts[index] = transform(contacts[index])
    }
}
