export function BrandMark({ size = 40 }: { size?: number }) {
  return (
    <span
      aria-hidden="true"
      className="grid place-items-center rounded-xl border border-current/30 bg-current/10 text-current shadow-glow"
      style={{ height: size, width: size }}
    >
      <svg fill="none" height="70%" viewBox="0 0 64 64" width="70%" xmlns="http://www.w3.org/2000/svg">
        <path d="M32 5 55 18v28L32 59 9 46V18L32 5Z" stroke="currentColor" strokeWidth="4" />
        <path d="M18 23h28M18 32h28M18 41h28" stroke="#b7ff3c" strokeLinecap="round" strokeWidth="4" />
        <path d="M32 10v48" stroke="#f5a524" strokeLinecap="round" strokeWidth="4" />
      </svg>
    </span>
  );
}
