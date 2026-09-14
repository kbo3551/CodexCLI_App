using System;
using System.Drawing;
using System.Runtime.InteropServices;

// Captures a window with PrintWindow so the target does not need to be in the foreground.
// Used only to review the UI during development.
public class WindowCapture
{
    [DllImport("user32.dll")]
    private static extern bool EnumWindows(EnumProc callback, IntPtr param);

    [DllImport("user32.dll")]
    private static extern uint GetWindowThreadProcessId(IntPtr window, out uint processId);

    [DllImport("user32.dll")]
    private static extern bool IsWindowVisible(IntPtr window);

    [DllImport("user32.dll")]
    private static extern bool GetWindowRect(IntPtr window, out RECT rect);

    [DllImport("user32.dll")]
    private static extern bool PrintWindow(IntPtr window, IntPtr deviceContext, uint flags);

    private delegate bool EnumProc(IntPtr window, IntPtr param);

    [StructLayout(LayoutKind.Sequential)]
    public struct RECT { public int Left, Top, Right, Bottom; }

    public static IntPtr FindLargestWindow(uint processId)
    {
        IntPtr best = IntPtr.Zero;
        int bestArea = 0;
        EnumWindows((window, param) =>
        {
            uint pid;
            GetWindowThreadProcessId(window, out pid);
            if (pid != processId || !IsWindowVisible(window)) return true;
            RECT rect;
            if (!GetWindowRect(window, out rect)) return true;
            int area = (rect.Right - rect.Left) * (rect.Bottom - rect.Top);
            if (area > bestArea) { bestArea = area; best = window; }
            return true;
        }, IntPtr.Zero);
        return best;
    }

    public static string Capture(IntPtr window, string path)
    {
        RECT rect;
        if (!GetWindowRect(window, out rect)) return "GetWindowRect failed";
        int width = rect.Right - rect.Left;
        int height = rect.Bottom - rect.Top;
        if (width <= 0 || height <= 0) return "invalid size " + width + "x" + height;
        using (Bitmap bitmap = new Bitmap(width, height))
        {
            using (Graphics graphics = Graphics.FromImage(bitmap))
            {
                IntPtr hdc = graphics.GetHdc();
                // PW_RENDERFULLCONTENT (2) is required for hardware-accelerated windows.
                bool ok = PrintWindow(window, hdc, 2);
                graphics.ReleaseHdc(hdc);
                bitmap.Save(path, System.Drawing.Imaging.ImageFormat.Png);
                return (ok ? "ok " : "partial ") + width + "x" + height;
            }
        }
    }
}
