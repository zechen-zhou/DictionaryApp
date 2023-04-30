package com.example.dictionaryapp.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class HomeViewModel : ViewModel() {

    // Creates a MutableLiveData object "_text"
    private val _text = MutableLiveData<String>().apply {
        value = "This is home Fragment"
    }

    // Assigns private variable "_text" to "text" so that we can access it from outside of this class
    val text: LiveData<String> = _text
}