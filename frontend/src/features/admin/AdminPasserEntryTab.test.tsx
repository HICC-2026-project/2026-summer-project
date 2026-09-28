import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { AdminPasserEntryTab } from "./AdminPasserEntryTab";
import { createAdminPasserEntry } from "./api";

// 관리자 합격자 수기 등록 폼의 핵심 규칙을 고정한다:
// 출처(sourceNote)가 없으면 제출을 막고, 성공하면 폼을 리셋하며 안내 메시지를 보여준다.

vi.mock("./api", () => ({
  createAdminPasserEntry: vi.fn(),
}));

const mockedCreate = vi.mocked(createAdminPasserEntry);

function fillRequiredFields() {
  fireEvent.click(screen.getByText("백엔드"));

  const gpaInput = screen.getByLabelText("합격 당시 학점");
  fireEvent.change(gpaInput, { target: { value: "3.8" } });

  const sourceNoteInput = screen.getByLabelText("출처 *");
  fireEvent.change(sourceNoteInput, { target: { value: "https://blog.example.com/passed-2026" } });
}

describe("AdminPasserEntryTab", () => {
  beforeEach(() => {
    mockedCreate.mockReset();
  });

  it("출처를 입력하지 않으면 제출 버튼이 비활성화되고 안내 문구를 보여준다", () => {
    render(<AdminPasserEntryTab />);
    fireEvent.click(screen.getByText("백엔드"));
    fireEvent.change(screen.getByLabelText("합격 당시 학점"), { target: { value: "3.8" } });

    expect(screen.getByText("출처(URL 또는 메모)를 입력해주세요.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "합격자 데이터 등록" })).toBeDisabled();
  });

  it("필수 항목을 모두 채우면 제출할 수 있고, 성공하면 폼을 리셋하고 성공 메시지를 보여준다", async () => {
    mockedCreate.mockResolvedValue({
      passerId: "11111111-1111-1111-1111-111111111111",
      status: "VERIFIED",
      message: "합격자 데이터가 등록되었습니다. 검수 없이 즉시 비교 데이터로 반영됩니다.",
    });

    render(<AdminPasserEntryTab />);
    fillRequiredFields();

    const submitButton = screen.getByRole("button", { name: "합격자 데이터 등록" });
    expect(submitButton).not.toBeDisabled();
    fireEvent.click(submitButton);

    await waitFor(() => expect(mockedCreate).toHaveBeenCalledTimes(1));
    expect(mockedCreate).toHaveBeenCalledWith(
      expect.objectContaining({
        jobType: "BACKEND",
        gpa: 3.8,
        sourceNote: "https://blog.example.com/passed-2026",
      }),
    );

    await waitFor(() =>
      expect(
        screen.getByText("합격자 데이터가 등록되었습니다. 검수 없이 즉시 비교 데이터로 반영됩니다."),
      ).toBeInTheDocument(),
    );
    // 폼 리셋: 출처 입력이 비워졌다
    expect((screen.getByLabelText("출처 *") as HTMLTextAreaElement).value).toBe("");
  });

  it("등록에 실패하면 오류 메시지를 보여주고 폼을 리셋하지 않는다", async () => {
    mockedCreate.mockRejectedValue(new Error("서버 오류"));

    render(<AdminPasserEntryTab />);
    fillRequiredFields();
    fireEvent.click(screen.getByRole("button", { name: "합격자 데이터 등록" }));

    await waitFor(() => expect(mockedCreate).toHaveBeenCalledTimes(1));
    await waitFor(() =>
      expect(screen.getByText("등록에 실패했습니다. 잠시 후 다시 시도해주세요.")).toBeInTheDocument(),
    );
    expect((screen.getByLabelText("출처 *") as HTMLTextAreaElement).value).toBe(
      "https://blog.example.com/passed-2026",
    );
  });

  it("개인 식별 정보 금지·공개 후기만 입력하라는 안내 문구를 보여준다", () => {
    render(<AdminPasserEntryTab />);
    expect(screen.getByText(/개인 식별 정보는 절대/)).toBeInTheDocument();
  });
});
