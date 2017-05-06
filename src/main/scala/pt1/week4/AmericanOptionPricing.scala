package pt1.week4

import breeze.linalg.{DenseMatrix, DenseVector, _}

import scala.math.{exp, sqrt}

object AmericanOptionPricing {

  def calculate(sharePriceLattice: DenseMatrix[Double], termInYears: Double, volatility: Double,
                numberOfPeriods: Int, interestRate: Double, dividendYield: Double, strikePrice: Double, isPut: Boolean)
               (implicit maturity: Int = numberOfPeriods): Double = {
    val result = calculatePricingMatrix(sharePriceLattice, termInYears, volatility, numberOfPeriods, interestRate, dividendYield, strikePrice, isPut)(maturity)
    result.apply(0, 0)
  }

  def earliestExercise(sharePriceLattice: DenseMatrix[Double], termInYears: Double, volatility: Double,
                       numberOfPeriods: Int, interestRate: Double, dividendYield: Double, strikePrice:
                       Double, isPut: Boolean)(implicit maturity: Int = numberOfPeriods): Int = {
    val pricingMatrix = calculatePricingMatrix(sharePriceLattice, termInYears, volatility, numberOfPeriods, interestRate, dividendYield, strikePrice, isPut)(maturity)
    val gainOnSaleWithStrikePrice = calculateProfitFromSaleAtTheMoment(sharePriceLattice, maturity, strikePrice, isPut)

    val whateverBigger = max(pricingMatrix, gainOnSaleWithStrikePrice)
    val isOptimalToExercise = gainOnSaleWithStrikePrice :== whateverBigger
    val notEqualIndexes = isOptimalToExercise.findAll(v => v)
    val first = notEqualIndexes.filter(pair => pair._1 <= pair._2 && gainOnSaleWithStrikePrice(pair._1, pair._2) != 0.0).minBy(_._2)
    first._2
  }

  private[week4] def calculateProfitFromSaleAtTheMoment(originalSharePriceLattice: DenseMatrix[Double], maturity: Int, strikePrice: Double, isPut: Boolean) = {
    val n = maturity + 1
    val sharePriceLattice =
      if (originalSharePriceLattice.rows == n && originalSharePriceLattice.cols == n) originalSharePriceLattice
      else copyWithSize(originalSharePriceLattice, n, n)
    val putCallMultiplier: Double = if (isPut) -1 else 1
    val result = DenseMatrix.zeros[Double](n, n)
    val zero = DenseVector.zeros[Double](n)
    val lastColumnIndex = maturity

    for (i <- 0 to lastColumnIndex) {
      result(::, i) := profitAtTheMoment(zero, sharePriceLattice, strikePrice, putCallMultiplier, i)
    }
    result
  }

  private def calculatePricingMatrix(originalSharePriceLattice: DenseMatrix[Double], termInYears: Double,
                                     volatility: Double, numberOfPeriods: Int, interestRate: Double,
                                     dividendYield: Double, strikePrice: Double, isPut: Boolean)(maturity: Int) = {
    val u: Double = exp(volatility * sqrt(termInYears / numberOfPeriods))
    val d: Double = 1 / u
    val q: Double = (exp((interestRate - dividendYield) * termInYears / numberOfPeriods) - d) / (u - d)
    val p: Double = 1 - q
    val periodRelatedDivisor: Double = exp(interestRate * termInYears / numberOfPeriods)
    val putCallMultiplier: Double = if (isPut) -1 else 1
    val n = maturity + 1
    val sharePriceLattice =
      if (originalSharePriceLattice.rows == n && originalSharePriceLattice.cols == n) originalSharePriceLattice
      else copyWithSize(originalSharePriceLattice, n, n)

    val result = DenseMatrix.zeros[Double](n, n)
    val zero = DenseVector.zeros[Double](n)
    val lastColumnIndex = maturity
    result(::, lastColumnIndex) := profitAtTheMoment(zero, sharePriceLattice, strikePrice, putCallMultiplier, lastColumnIndex)

    val columnsFromPriorToLastOneDownToFirst = (lastColumnIndex - 1) to 0 by -1
    for (i <- columnsFromPriorToLastOneDownToFirst) {
      val previousColumn = result(::, i + 1)
      val previousColumnShiftedOneDown = DenseVector.vertcat(previousColumn(1 until previousColumn.length), DenseVector.zeros[Double](1))
      val currentColumn = ((previousColumnShiftedOneDown * q) + (previousColumn * p)) / periodRelatedDivisor
      val profit = profitAtTheMoment(zero, sharePriceLattice, strikePrice, putCallMultiplier, i)
      result(::, i) += max(profit, currentColumn)
    }
    result
  }

  private def profitAtTheMoment(zero: DenseVector[Double], sharePriceLattice: DenseMatrix[Double],
                                strikePrice: Double, putCallMultiplier: Double, columnIndex: Int) = {
    max(zero, (sharePriceLattice(::, columnIndex) - strikePrice) * putCallMultiplier)
  }

  private def copyWithSize(matrix: DenseMatrix[Double], rows: Int, cols: Int) = {
    val result = DenseMatrix.zeros[Double](rows, cols)
    for (i <- 0 until cols) {
      val column = matrix(::, i)
      result(::, i) += column.slice(0, rows)
    }
    result
  }
}
