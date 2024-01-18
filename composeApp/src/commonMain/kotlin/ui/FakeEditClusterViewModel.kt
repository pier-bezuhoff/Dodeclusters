package ui

import data.Cluster
import kotlinx.serialization.Serializable

// NOTE: waiting for decompose 3.0-stable for a real VM impl
// (use state flows in vM + in ui convert to state with .collectAsStateWithLc)
class FakeEditClusterViewModel(
    cluster: Cluster = Cluster.SAMPLE
) {
}

