import { describe, expect, it } from 'vitest'
import { useState } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfirmProvider, useConfirm } from './dialog'
import { vi as text } from '../i18n/vi'

function Asker({ danger = false }: { danger?: boolean }) {
  const confirm = useConfirm()
  const [answer, setAnswer] = useState('chưa hỏi')
  return (
    <>
      <button onClick={async () => setAnswer(String(await confirm({ title: 'Xóa?', body: 'Không hoàn tác được.', confirmLabel: 'Xóa hẳn', danger })))}>
        Hỏi
      </button>
      <output>{answer}</output>
    </>
  )
}

const setup = (danger?: boolean) => {
  render(<ConfirmProvider><Asker danger={danger} /></ConfirmProvider>)
  return userEvent.setup()
}

describe('useConfirm()', () => {
  it('mở hộp có tên và mô tả, bấm xác nhận thì trả true', async () => {
    const user = setup()
    await user.click(screen.getByRole('button', { name: 'Hỏi' }))

    const dialog = screen.getByRole('dialog', { name: 'Xóa?' })
    expect(dialog).toHaveAccessibleDescription('Không hoàn tác được.')

    await user.click(screen.getByRole('button', { name: 'Xóa hẳn' }))
    expect(screen.getByRole('status')).toHaveTextContent('true')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('bấm Hủy thì trả false', async () => {
    const user = setup()
    await user.click(screen.getByRole('button', { name: 'Hỏi' }))
    await user.click(screen.getByRole('button', { name: text.common.cancel }))

    expect(screen.getByRole('status')).toHaveTextContent('false')
  })

  it('thao tác nguy hiểm: focus nằm ở nút Hủy, nút xác nhận màu đỏ', async () => {
    const user = setup(true)
    await user.click(screen.getByRole('button', { name: 'Hỏi' }))

    expect(screen.getByRole('button', { name: text.common.cancel })).toHaveFocus()
    expect(screen.getByRole('button', { name: 'Xóa hẳn' })).toHaveClass('bg-red-600')
  })

  it('Escape đóng hộp và trả false', async () => {
    const user = setup()
    await user.click(screen.getByRole('button', { name: 'Hỏi' }))
    screen.getByRole('dialog').dispatchEvent(new Event('cancel', { cancelable: true }))

    expect(await screen.findByText('false')).toBeInTheDocument()
  })
})
