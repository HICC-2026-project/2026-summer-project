import { PRIMARY, TODAY } from "./data";

export function dday(dateStr: string): string {
  const diff = Math.ceil((new Date(dateStr).getTime() - TODAY.getTime()) / 86400000);
  return diff <= 0 ? "마감" : `D-${diff}`;
}

export function ddayColor(dateStr: string): string {
  const diff = Math.ceil((new Date(dateStr).getTime() - TODAY.getTime()) / 86400000);
  return diff <= 30 ? "#E5484D" : "#9797A1";
}

export function fmtDate(dateStr: string): string {
  const [, month, day] = dateStr.split("-");
  return `${parseInt(month, 10)}월 ${parseInt(day, 10)}일`;
}

export interface ChipStyle {
  background: string;
  color: string;
  borderColor: string;
}

export function chipStyle(selected: boolean): ChipStyle {
  return selected
    ? { background: `color-mix(in srgb, ${PRIMARY} 10%, #fff)`, color: PRIMARY, borderColor: PRIMARY }
    : { background: "#F6F5FA", color: "#61616C", borderColor: "#EAE9F1" };
}
