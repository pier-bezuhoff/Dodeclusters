package domain.model

import domain.ColorAsCss
import domain.Ix

// encapsulation of colrs & styles within ObjectModel
data class Styling(
    val colors: MutableMap<Ix, ColorAsCss>,
    val labels: MutableMap<Ix, String>,
)
