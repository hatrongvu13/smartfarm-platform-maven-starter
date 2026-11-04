import React from 'react'
import { vi } from '../i18n/vi'

type State = { error?: Error }

export class AppErrorBoundary extends React.Component<React.PropsWithChildren, State> {
  state: State = {}
  static getDerivedStateFromError(error: Error): State { return { error } }
  componentDidCatch(error: Error, info: React.ErrorInfo) {
    console.error('FarmVerse UI render failure', error, info.componentStack)
  }
  render() {
    if (!this.state.error) return this.props.children
    return (
      <main className="mx-auto mt-16 max-w-xl rounded-xl border border-red-200 bg-red-50 p-6">
        <h1 className="text-lg font-semibold text-red-900">{vi.crash.title}</h1>
        <p className="mt-2 text-sm text-red-800">{vi.crash.detail}</p>
        <div className="mt-4 flex gap-2">
          <button className="rounded-lg bg-red-700 px-4 py-2 text-sm text-white" onClick={() => location.reload()}>{vi.crash.reload}</button>
          <button className="rounded-lg border border-red-300 px-4 py-2 text-sm text-red-900" onClick={() => location.assign('/')}>{vi.crash.home}</button>
        </div>
      </main>
    )
  }
}
