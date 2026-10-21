import useBaseUrl from '@docusaurus/useBaseUrl';
import clsx from 'clsx';
import React from 'react';
import styles from './styles.module.css';

type Device = 'ios' | 'ipad' | 'android' | 'duo';

const LABELS: Record<Device, string> = {
  ios: 'iOS',
  ipad: 'iPadOS',
  android: 'Android',
  duo: 'iPhone Duo',
};

/** One recording in the frame of its device; an iPhone Duo recording has the device in it, folding, on a dark stage. */
function Clip({ name, device }: { name: string; device: Device }) {
  const base = useBaseUrl(`/video/${name}-${device}`);
  return (
    <figure
      className={clsx(
        styles.device,
        device === 'ipad' && styles.ipad,
        device === 'duo' && styles.duo
      )}
    >
      <div className={styles.frame}>
        <video
          className={styles.video}
          poster={`${base}.jpg`}
          autoPlay
          muted
          loop
          playsInline
          preload="metadata"
        >
          <source src={`${base}.webm`} type="video/webm" />
          <source src={`${base}.mp4`} type="video/mp4" />
        </video>
      </div>
      <figcaption className={styles.label}>{LABELS[device]}</figcaption>
    </figure>
  );
}

/**
 * A recording of the library in an app in the Showcase's look, one per platform, side by side. The files are
 * `static/video/<name>-<ios|ipad|android|duo>.<webm|mp4|jpg>`.
 */
export function DeviceVideo({
  name,
  devices = ['ios', 'android'],
  caption,
}: {
  name: string;
  devices?: Device[];
  caption?: string;
}) {
  return (
    <div className={styles.videos}>
      <div className={styles.row}>
        {devices.map((device) => (
          <Clip key={device} name={name} device={device} />
        ))}
      </div>
      {caption && <p className={styles.caption}>{caption}</p>}
    </div>
  );
}
