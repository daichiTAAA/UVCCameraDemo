const dateTimeFormatter = new Intl.DateTimeFormat('ja-JP', {
  dateStyle: 'medium',
  timeStyle: 'short',
})

const dateFormatter = new Intl.DateTimeFormat('ja-JP', {
  dateStyle: 'medium',
})

export const formatDateTime = (value?: string) => {
  if (!value) return 'N/A'
  return dateTimeFormatter.format(new Date(value))
}

export const formatDate = (value?: string) => {
  if (!value) return 'N/A'
  return dateFormatter.format(new Date(value))
}

export const formatBytes = (size?: number) => {
  if (size === undefined || size === null) return 'Unknown'
  if (size === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const exponent = Math.min(Math.floor(Math.log(size) / Math.log(1024)), units.length - 1)
  const value = size / 1024 ** exponent
  return `${value.toFixed(1)} ${units[exponent]}`
}
