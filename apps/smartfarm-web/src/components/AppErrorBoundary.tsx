import React from 'react'

type State = { error?: Error }

export class AppErrorBoundary extends React.Component<React.PropsWithChildren, State> {
  state: State = {}
  static getDerivedStateFromError(error: Error): State { return { error } }
  componentDidCatch(error: Error, info: React.ErrorInfo) {
    console.error('SmartFarm UI render failure', error, info.componentStack)
  }
  render() {
    if (!this.state.error) return this.props.children
    return (
      <main className="mx-auto mt-16 max-w-xl rounded-xl border border-red-200 bg-red-50 p-6">
        <h1 className="text-lg font-semibold text-red-900">Không thể hiển thị màn hình</h1>
        <p className="mt-2 text-sm text-red-800">Dữ liệu của bạn chưa bị thay đổi. Hãy tải lại trang hoặc quay về tổng quan.</p>
        <div className="mt-4 flex gap-2">
          <button className="rounded-lg bg-red-700 px-4 py-2 text-sm text-white" onClick={() => location.reload()}>Tải lại</button>
          <button className="rounded-lg border border-red-300 px-4 py-2 text-sm text-red-900" onClick={() => location.assign('/')}>Về tổng quan</button>
        </div>
      </main>
    )
  }
}
