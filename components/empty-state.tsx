export function EmptyState({ title, body }: { title: string; body: string }) {
  return (
    <div className="mt-5 rounded-xl border border-forge/25 bg-forge/10 p-4">
      <p className="font-bold text-forge">{title}</p>
      <p className="mt-1 text-sm text-white/60">{body}</p>
    </div>
  );
}
