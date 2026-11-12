/** Existing honor finish is opt-in metadata; unknown/new PBR art keeps its authored materials. */
export function applyModelMaterialPolicy(material, spec, maxTextureSize) {
  if (!material) return;
  if (spec.remote && spec.presentation.legacyHonorFinish === true && material.isMeshStandardMaterial) {
    if (material.metalness > .55) {
      material.roughness = Math.max(.14,Math.min(.32,material.roughness*.78));
      material.envMapIntensity = 1.3;
    }
    if (material.clearcoat > 0 && material.metalness > .2) {
      material.clearcoat = Math.max(material.clearcoat,.5);
      material.clearcoatRoughness = Math.max(.1,Math.min(.16,material.clearcoatRoughness??.16));
    }
    if (material.name.includes('vitreous enamel')) { material.clearcoat=.65;material.roughness=Math.min(.21,material.roughness); }
  }
  if (material.transmission > 0 && maxTextureSize < 4096) {
    // Explicit low-capability solid-facet approximation. Geometry and authored IOR remain intact.
    material.transmission=0;material.opacity=1;material.transparent=false;
    material.roughness=Math.max(.15,material.roughness);material.needsUpdate=true;
  }
}
