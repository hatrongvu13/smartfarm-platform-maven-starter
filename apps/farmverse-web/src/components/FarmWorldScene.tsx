import { useEffect, useRef } from 'react'
import * as THREE from 'three'

export type FarmWorldSceneProps = {
  className?: string
  variant?: 'wide' | 'compact'
}

export default function FarmWorldScene({ className = '', variant = 'wide' }: FarmWorldSceneProps) {
  const hostRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const host = hostRef.current
    if (!host) return

    const scene = new THREE.Scene()
    scene.background = new THREE.Color('#b8d5c2')
    scene.fog = new THREE.Fog('#b8d5c2', 19, 44)

    const camera = new THREE.PerspectiveCamera(39, 1, 0.1, 100)
    camera.position.set(11, 10, 16)
    camera.lookAt(0, 1.3, 0)

    let renderer: THREE.WebGLRenderer
    try {
      renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false })
    } catch (error) {
      console.error('Could not initialize the farm-world WebGL scene.', error)
      host.classList.add('farm-world-unavailable')
      return () => host.classList.remove('farm-world-unavailable')
    }
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.7))
    renderer.shadowMap.enabled = true
    renderer.shadowMap.type = THREE.PCFSoftShadowMap
    renderer.outputColorSpace = THREE.SRGBColorSpace
    renderer.toneMapping = THREE.ACESFilmicToneMapping
    renderer.toneMappingExposure = 1.12
    host.appendChild(renderer.domElement)

    scene.add(new THREE.HemisphereLight('#e9fff0', '#4b5638', 2.1))
    const sun = new THREE.DirectionalLight('#fff1cd', 3.3)
    sun.position.set(-8, 14, 7)
    sun.castShadow = true
    sun.shadow.mapSize.set(1024, 1024)
    scene.add(sun)

    const material = (color: string, roughness = 0.86, extra: THREE.MeshStandardMaterialParameters = {}) =>
      new THREE.MeshStandardMaterial({ color, roughness, ...extra })
    const groundMat = material('#718e52')
    const grassMat = material('#8ba661')
    const darkGrassMat = material('#5b7844')
    const soilMat = material('#8a593d')
    const woodMat = material('#79513a')
    const paleWoodMat = material('#c89b61')
    const leafMat = material('#568358')
    const lightLeafMat = material('#82a963')
    const crystalMat = material('#87e7d0', 0.2, { emissive: '#2cb99e', emissiveIntensity: 0.75, metalness: 0.18 })
    const goldMat = material('#f3cb72', 0.28, { emissive: '#b67735', emissiveIntensity: 0.22, metalness: 0.32 })

    const addMesh = (
      geometry: THREE.BufferGeometry,
      mat: THREE.Material,
      parent: THREE.Object3D = scene,
      position: [number, number, number] = [0, 0, 0],
      castShadow = true,
    ) => {
      const mesh = new THREE.Mesh(geometry, mat)
      mesh.position.set(...position)
      mesh.castShadow = castShadow
      mesh.receiveShadow = true
      parent.add(mesh)
      return mesh
    }

    const ground = addMesh(new THREE.PlaneGeometry(48, 48), groundMat, scene, [0, -0.18, 0], false)
    ground.rotation.x = -Math.PI / 2

    const island = addMesh(new THREE.CylinderGeometry(8.8, 10.2, 0.75, 9, 1), grassMat, scene, [0, -0.25, 0])
    island.scale.z = 0.77
    const rim = addMesh(new THREE.CylinderGeometry(9.05, 10.25, 0.18, 9), darkGrassMat, scene, [0, -0.62, 0], false)
    rim.scale.z = 0.78

    const path = addMesh(new THREE.BoxGeometry(2.1, 0.09, 11), material('#c3a173'), scene, [0.1, 0.18, 1.5], false)
    path.rotation.y = -0.15
    const pathEdge = addMesh(new THREE.BoxGeometry(0.17, 0.1, 10.5), goldMat, scene, [-0.98, 0.2, 1.5], false)
    pathEdge.rotation.y = -0.15

    const plotPositions: [number, number, number][] = [
      [-4.2, 0.18, 2.5], [-2.3, 0.18, 2.5], [3.3, 0.18, 2.5], [5.1, 0.18, 2.5],
      [-4.2, 0.18, 4.35], [-2.3, 0.18, 4.35], [3.3, 0.18, 4.35], [5.1, 0.18, 4.35],
    ]
    plotPositions.forEach((position, plotIndex) => {
      const plot = addMesh(new THREE.BoxGeometry(1.55, 0.12, 1.45), soilMat, scene, position)
      plot.rotation.y = -0.12
      for (let row = 0; row < 3; row += 1) {
        for (let col = 0; col < 4; col += 1) {
          const x = position[0] - 0.5 + col * 0.34
          const z = position[2] - 0.43 + row * 0.42
          const stem = addMesh(new THREE.CylinderGeometry(0.025, 0.04, 0.3, 5), leafMat, scene, [x, 0.4, z], false)
          stem.rotation.z = (col % 2 ? 1 : -1) * 0.16
          const crop = addMesh(
            new THREE.IcosahedronGeometry(plotIndex % 3 === 0 ? 0.16 : 0.11, 0),
            plotIndex % 3 === 0 ? goldMat : lightLeafMat,
            scene,
            [x + 0.04, 0.57, z - 0.02],
            false,
          )
          crop.scale.set(0.78, 1.2, 0.78)
        }
      }
    })

    const cabin = new THREE.Group()
    cabin.position.set(0, 0.12, -2.15)
    scene.add(cabin)
    addMesh(new THREE.BoxGeometry(3, 1.8, 2.25), paleWoodMat, cabin, [0, 0.95, 0])
    const roof = addMesh(new THREE.ConeGeometry(2.25, 1.25, 4), material('#8f5841'), cabin, [0, 2.45, 0.02])
    roof.rotation.y = Math.PI / 4
    roof.scale.set(1, 1, 0.82)
    addMesh(new THREE.BoxGeometry(0.62, 1.05, 0.12), woodMat, cabin, [0, 0.55, 1.18])
    addMesh(new THREE.BoxGeometry(0.55, 0.55, 0.1), crystalMat, cabin, [-0.92, 1.22, 1.16], false)
    addMesh(new THREE.BoxGeometry(0.55, 0.55, 0.1), crystalMat, cabin, [0.92, 1.22, 1.16], false)
    addMesh(new THREE.BoxGeometry(0.35, 0.55, 0.5), woodMat, cabin, [0.7, 2.1, -0.3])
    const sign = addMesh(new THREE.BoxGeometry(1.1, 0.28, 0.1), goldMat, cabin, [0, 1.96, 1.2], false)

    const createTree = (x: number, z: number, scale = 1) => {
      const tree = new THREE.Group()
      tree.position.set(x, 0.1, z)
      tree.scale.setScalar(scale)
      scene.add(tree)
      addMesh(new THREE.CylinderGeometry(0.16, 0.24, 1.15, 7), woodMat, tree, [0, 0.56, 0])
      addMesh(new THREE.IcosahedronGeometry(0.88, 1), leafMat, tree, [0, 1.42, 0])
      addMesh(new THREE.IcosahedronGeometry(0.6, 1), lightLeafMat, tree, [0.42, 1.68, 0.08], false)
      addMesh(new THREE.IcosahedronGeometry(0.18, 0), goldMat, tree, [0.26, 1.5, 0.55], false)
      return tree
    }
    const trees = [
      createTree(-6.1, -2.5, 1.15), createTree(-7.1, 0.1, 0.82), createTree(6.6, -2.1, 1.15),
      createTree(7.2, 0.6, 0.92), createTree(-5.9, 5.1, 0.84), createTree(6.5, 5.3, 0.88),
    ]

    const pond = addMesh(
      new THREE.CircleGeometry(1.1, 32),
      material('#5bb6ae', 0.22, { metalness: 0.12, emissive: '#126b68', emissiveIntensity: 0.22 }),
      scene,
      [0.2, 0.16, -5.8],
      false,
    )
    pond.rotation.x = -Math.PI / 2
    const pondRim = addMesh(new THREE.TorusGeometry(1.12, 0.12, 8, 32), goldMat, scene, [0.2, 0.2, -5.8], false)
    pondRim.rotation.x = Math.PI / 2

    const crystals: THREE.Mesh[] = []
    for (const [x, z, height] of [[-4.2, -4.6, 1.1], [4.8, -4.4, 1.45], [0, -6.6, 0.9]] as const) {
      const crystal = addMesh(new THREE.OctahedronGeometry(0.48, 0), crystalMat, scene, [x, height / 2, z])
      crystal.scale.y = height
      crystals.push(crystal)
      addMesh(new THREE.TorusGeometry(0.58, 0.025, 6, 28), goldMat, scene, [x, 0.23, z], false).rotation.x = Math.PI / 2
    }

    const windmill = new THREE.Group()
    windmill.position.set(-5.7, 0.25, -4.1)
    scene.add(windmill)
    addMesh(new THREE.CylinderGeometry(0.4, 0.68, 2.5, 6), paleWoodMat, windmill, [0, 1.15, 0])
    const rotor = new THREE.Group()
    rotor.position.set(0, 2.3, 0.42)
    windmill.add(rotor)
    addMesh(new THREE.SphereGeometry(0.15, 12, 12), goldMat, rotor)
    for (let blade = 0; blade < 4; blade += 1) {
      const sail = addMesh(new THREE.BoxGeometry(0.2, 1.35, 0.07), material('#f2e4bf'), rotor, [0, 0.82, 0])
      sail.rotation.z = blade * Math.PI / 2
    }

    const floatingIslands: THREE.Mesh[] = []
    for (const [x, y, z, size] of [[-7.8, 4.3, -4.1, 0.7], [7.4, 5.1, -5, 0.85], [4.8, 6.1, 1.6, 0.55]] as const) {
      const rock = addMesh(new THREE.DodecahedronGeometry(size, 0), material('#8c896d'), scene, [x, y, z])
      rock.scale.y = 0.7
      addMesh(new THREE.ConeGeometry(size * 0.8, 1, 5), darkGrassMat, scene, [x, y + 0.4, z], false)
      floatingIslands.push(rock)
    }

    const sunOrb = addMesh(
      new THREE.SphereGeometry(0.67, 24, 24),
      material('#ffe9aa', 0.2, { emissive: '#f2ab48', emissiveIntensity: 0.7 }),
      scene,
      [-7.7, 8.8, -10],
      false,
    )
    const halo = addMesh(new THREE.TorusGeometry(0.94, 0.035, 8, 48), goldMat, scene, [-7.7, 8.8, -10], false)
    halo.rotation.x = 0.8

    const resize = () => {
      const width = host.clientWidth
      const height = host.clientHeight
      if (width === 0 || height === 0) return
      camera.aspect = width / height
      camera.updateProjectionMatrix()
      renderer.setSize(width, height, false)
    }
    const resizeObserver = new ResizeObserver(resize)
    resizeObserver.observe(host)
    resize()

    let pointerX = 0
    let pointerY = 0
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    const onPointerMove = (event: PointerEvent) => {
      const bounds = host.getBoundingClientRect()
      pointerX = ((event.clientX - bounds.left) / bounds.width - 0.5) * 2
      pointerY = ((event.clientY - bounds.top) / bounds.height - 0.5) * 2
    }
    host.addEventListener('pointermove', onPointerMove)

    const clock = new THREE.Clock()
    let frame = 0
    const animate = () => {
      if (!reducedMotion) frame = window.requestAnimationFrame(animate)
      const time = clock.getElapsedTime()
      camera.position.x += (11 + pointerX * 0.8 - camera.position.x) * 0.018
      camera.position.y += (10 - pointerY * 0.35 - camera.position.y) * 0.018
      camera.lookAt(0, 1.3, 0)
      rotor.rotation.z = time * 0.45
      crystals.forEach((crystal, index) => {
        crystal.rotation.y = time * 0.28 + index
        crystal.position.y = [0.55, 0.72, 0.45][index] + Math.sin(time * 1.4 + index) * 0.1
      })
      floatingIslands.forEach((rock, index) => {
        rock.position.y += ( [4.3, 5.1, 6.1][index] + Math.sin(time * 0.5 + index) * 0.12 - rock.position.y) * 0.025
        rock.rotation.y = Math.sin(time * 0.2 + index) * 0.1
      })
      trees.forEach((tree, index) => {
        tree.rotation.z = Math.sin(time * 0.7 + index) * 0.018
      })
      sign.material = time % 5 < 2.5 ? goldMat : crystalMat
      sunOrb.position.y = 8.8 + Math.sin(time * 0.3) * 0.12
      halo.rotation.z = -time * 0.08
      renderer.render(scene, camera)
    }
    animate()

    return () => {
      window.cancelAnimationFrame(frame)
      resizeObserver.disconnect()
      host.removeEventListener('pointermove', onPointerMove)
      renderer.dispose()
      scene.traverse((object) => {
        if (object instanceof THREE.Mesh) {
          object.geometry.dispose()
          if (Array.isArray(object.material)) object.material.forEach((entry) => entry.dispose())
          else object.material.dispose()
        }
      })
      renderer.domElement.remove()
    }
  }, [])

  return (
    <div
      ref={hostRef}
      className={`farm-world-scene farm-world-scene--${variant} ${className}`}
      aria-label="Mô hình nông trại huyền huyễn 3D với ruộng, nhà gỗ, hồ pha lê và đảo bay"
      role="img"
    />
  )
}
