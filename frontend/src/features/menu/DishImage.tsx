import { useState } from 'react';
export function DishImage({ src, name }: { src: string; name: string }) {
  const [failed, setFailed] = useState(false);
  return failed ? <div className="image-fallback" role="img" aria-label={`Image unavailable for ${name}`}>Image unavailable</div> : <img src={src} alt={name} loading="lazy" onError={() => setFailed(true)} />;
}
