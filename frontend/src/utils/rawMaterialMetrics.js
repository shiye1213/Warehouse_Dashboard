export function calculateAvailableRate(onHandValue, availableValue) {
  const onHand = Number(onHandValue || 0)
  const available = Number(availableValue || 0)
  if (onHand <= 0) return 0
  return Math.min(1, Math.max(0, available / onHand))
}
