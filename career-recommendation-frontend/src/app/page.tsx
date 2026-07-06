import { Button } from "@/components/ui/button";

export default function Home() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4">
      <h1 className="text-2xl font-semibold">커리어 추천 서비스</h1>
      <p className="text-muted-foreground">디자인 반영 전 placeholder 화면입니다.</p>
      <Button>버튼 컴포넌트 동작 확인</Button>
    </div>
  );
}
