package org.monora.uprotocol.client.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import org.monora.uprotocol.client.android.data.GitHubDataRepository
import javax.inject.Inject

@HiltViewModel
class ContributorsDataViewModel @Inject internal constructor(
    gitHubDataRepository: GitHubDataRepository,
) : ViewModel() {
    val contributors = liveData(viewModelScope.coroutineContext) {
        emit(gitHubDataRepository.getContributors())
    }
}