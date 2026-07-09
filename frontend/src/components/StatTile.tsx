export default function StatTile({
  label,
  value,
  icon,
}: {
  label: string;
  value: string | number;
  icon?: string;
}) {
  return (
    <div className="card flex items-center gap-4">
      {icon && <span className="text-2xl">{icon}</span>}
      <div>
        <div className="text-2xl font-bold text-parchment">{value}</div>
        <div className="text-xs uppercase tracking-wide text-slate-500">{label}</div>
      </div>
    </div>
  );
}
