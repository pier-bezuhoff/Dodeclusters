package domain.io

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.SequenceStyle
import com.charleskorn.kaml.UnknownPolymorphicTypeException
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import data.geometry.Circle
import data.geometry.CircleOrLine
import data.geometry.Line
import domain.expressions.CircleConstruct
import domain.expressions.Expr
import domain.expressions.Expression
import domain.expressions.PointConstruct
import kotlinx.serialization.SerializationException
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

val Yaml4DdcV3 = Yaml(
    EmptySerializersModule(),
//    SerializersModule { // shoudnt be necessary cuz it's closed/sealed polymorphism
//        polymorphic(DdcV3.Token::class) {
//            subclass(DdcV3.Token.Point::class)
//            subclass(DdcV3.Token.Circle::class)
//            subclass(DdcV3.Token.ArcPath::class)
//        }
//        polymorphic(PointConstruct::class) {
//            subclass(PointConstruct.Concrete::class)
//            subclass(PointConstruct.Dynamic::class)
//        }
//        polymorphic(CircleConstruct::class) {
//            subclass(CircleConstruct.Concrete::class)
//            subclass(CircleConstruct.Dynamic::class)
//        }
//        polymorphic(CircleOrLine::class) {
//            subclass(Circle::class)
//            subclass(Line::class)
//        }
//        polymorphic(Expression::class) {
//            subclass(Expression.Just::class)
//            subclass(Expression.OneOf::class)
//        }
//        polymorphic(Expr::class) {
//            subclass(Expr.Incidence::class)
//            subclass(Expr.CircleByCenterAndRadius::class)
//            subclass(Expr.LineBy2Points::class)
//            subclass(Expr.CircleBy3Points::class)
//            subclass(Expr.CircleByPencilAndPoint::class)
//            subclass(Expr.CircleInversion::class)
//            subclass(Expr.Intersection::class)
//            subclass(Expr.CircleInterpolation::class)
//            subclass(Expr.CircleExtrapolation::class)
//            subclass(Expr.LoxodromicMotion::class)
//        }
//    },
    YamlConfiguration(
        encodeDefaults = true,
        strictMode = false,
        polymorphismStyle = PolymorphismStyle.Property
    )
)

val Yaml4DdcV2 = Yaml(
    SerializersModule { // shoudnt be necessary cuz it's closed/sealed polymorphism
        polymorphic(DdcV2.Token::class) {
            subclass(DdcV2.Token.Cluster::class)
            subclass(DdcV2.Token.Circle::class)
            subclass(DdcV2.Token.Line::class)
        }
        polymorphic(CircleOrLine::class) {
            subclass(Circle::class)
            subclass(Line::class)
        }
    },
    YamlConfiguration(
        encodeDefaults = true,
        strictMode = false,
        polymorphismStyle = PolymorphismStyle.Property
    )
)

val Yaml4DdcV1 = Yaml(
    SerializersModule {
        polymorphic(DdcV1.Token::class) {
            subclass(DdcV1.Token.Cluster::class)
            subclass(DdcV1.Token.Circle::class)
        }
    },
    YamlConfiguration(
        encodeDefaults = true,
        strictMode = false,
        polymorphismStyle = PolymorphismStyle.Property
    )
)

// TODO: coroutines
@Throws(SerializationException::class, IllegalArgumentException::class)
fun parseDdcV3(content: String): DdcV3 =
    Yaml4DdcV3.decodeFromString(DdcV3.serializer(), content)

@Throws(SerializationException::class, IllegalArgumentException::class)
fun parseDdcV2(content: String): DdcV2 =
    Yaml4DdcV2.decodeFromString(DdcV2.serializer(), content)

@Throws(SerializationException::class, IllegalArgumentException::class)
fun parseDdcV1(content: String): DdcV1 =
    Yaml4DdcV1.decodeFromString(DdcV1.serializer(), content)
