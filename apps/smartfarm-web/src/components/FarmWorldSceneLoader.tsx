import { lazy, Suspense } from 'react'
import type { FarmWorldSceneProps } from './FarmWorldScene'

const FarmWorldScene = lazy(() => import('./FarmWorldScene'))

export default function FarmWorldSceneLoader(props: FarmWorldSceneProps) {
  return (
    <Suspense fallback={<div className={`farm-world-scene ${props.className ?? ''}`} />}>
      <FarmWorldScene {...props} />
    </Suspense>
  )
}
