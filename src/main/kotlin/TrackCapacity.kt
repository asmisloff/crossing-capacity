internal sealed interface TrackCapacity {

    operator fun plus(other: TrackCapacity): TrackCapacity

    companion object {
        fun createInstance(
            gp: GeneralParameters,
            md: MotionDimensions,
            oddRoutesPresent: Boolean,
            evenRoutesPresent: Boolean,
            period: Double
        ): TrackCapacity {
            if (period < 0) return FailedTrackCapacity

            val oddTerm = when (oddRoutesPresent) {
                true -> CapacityTermImpl.createInstance(gp, md.odd, period)
                false -> WasNotModeled
            }
            val evenTerm = when (evenRoutesPresent) {
                true -> CapacityTermImpl.createInstance(gp, md.even, period)
                false -> WasNotModeled
            }
            return TrackCapacityImpl(oddTerm, evenTerm)
        }
    }
}


internal class TrackCapacityImpl(val odd: CapacityTerm, val even: CapacityTerm) : TrackCapacity {
    override fun plus(other: TrackCapacity): TrackCapacity {
        return when (other) {
            FailedTrackCapacity -> FailedTrackCapacity
            is TrackCapacityImpl -> TrackCapacityImpl(this.odd + other.odd, this.even + other.even)
        }
    }
}


internal object FailedTrackCapacity : TrackCapacity {
    override operator fun plus(other: TrackCapacity): TrackCapacity {
        return FailedTrackCapacity
    }
}


internal sealed interface CapacityTerm {
    operator fun plus(other: CapacityTerm): CapacityTerm
}


internal object WasNotModeled : CapacityTerm {
    override operator fun plus(other: CapacityTerm): CapacityTerm {
        return other
    }
}


internal class CapacityTermImpl(
    val primaryCargo: Int,
    val secondaryPassenger: Int,
    val primaryPassenger: Int,
    val secondaryCargo: Int
) : CapacityTerm {

    fun isTrivial(): Boolean {
        return primaryCargo == 0 && secondaryPassenger == 0 && primaryPassenger == 0 && secondaryCargo == 0
    }

    override operator fun plus(other: CapacityTerm): CapacityTerm {
        return when (other) {
            is CapacityTermImpl -> CapacityTermImpl(
                primaryCargo = this.primaryCargo + other.primaryCargo,
                secondaryPassenger = this.secondaryPassenger + other.secondaryPassenger,
                primaryPassenger = this.primaryPassenger + other.primaryPassenger,
                secondaryCargo = this.secondaryCargo + other.secondaryCargo
            )
            WasNotModeled -> this
        }
    }

    companion object {
        fun createInstance(
            gp: GeneralParameters,
            md: UnidirectionalMotionDimensions,
            period: Double
        ): CapacityTermImpl {
            assert(period >= 0)
            if (period == 0.0) return CapacityTermImpl(0, 0, 0, 0)

            val t = (1440 - gp.window) * gp.alphaS * gp.alphaT * gp.alphaU
            val s = gp.expectedInterval * (md.secondary.ratedQty() + md.suburban.ratedQty())
            val prim = ((t - s) / period).toInt()
            val sec = md.secondary.qty + md.suburban.qty
            return when (md.primaryMotionType) {
                MotionType.Cargo -> CapacityTermImpl(prim, sec, 0, 0)
                MotionType.Passenger -> CapacityTermImpl(0, 0, prim, sec)
            }
        }
    }
}


interface GeneralParameters {
    val window: Int
    val alphaS: Double
    val alphaT: Double
    val alphaU: Double
    val expectedInterval: Int
}


enum class MotionType { Cargo, Passenger }


internal class MotionDimensions(val odd: UnidirectionalMotionDimensions, val even: UnidirectionalMotionDimensions) {
    fun toTrackCapacity(): TrackCapacityImpl {
        return TrackCapacityImpl(odd.toCapacityTerm(), even.toCapacityTerm())
    }
}


internal class UnidirectionalMotionDimensions(
    val primaryMotionType: MotionType,
    val primaryHeavy: TrainPairs,
    val primary: TrainPairs,
    val secondary: TrainPairs,
    val suburban: TrainPairs
) {

    fun toCapacityTerm(): CapacityTermImpl {
        val prim = primary.qty + primaryHeavy.qty
        val sec = secondary.qty + suburban.qty
        return when (primaryMotionType) {
            MotionType.Cargo -> CapacityTermImpl(prim, sec, 0, 0)
            MotionType.Passenger -> CapacityTermImpl(0, 0, prim, sec)
        }
    }
}


internal class TrainPairs(val qty: Int, val removalCoefficient: Int) {
    fun ratedQty() = qty * removalCoefficient
}