import AppKit
import AVFoundation

let source = URL(fileURLWithPath: CommandLine.arguments[1])
let directory = URL(fileURLWithPath: CommandLine.arguments[2], isDirectory: true)
let asset = AVURLAsset(url: source)
let generator = AVAssetImageGenerator(asset: asset)
generator.appliesPreferredTrackTransform = true
generator.requestedTimeToleranceBefore = .zero
generator.requestedTimeToleranceAfter = .zero
for second in stride(from: 1, through: 17, by: 2) {
    do {
        let frame = try generator.copyCGImage(at: CMTime(value: Int64(second), timescale: 1), actualTime: nil)
        let bitmap = NSBitmapImageRep(cgImage: frame)
        let output = directory.appendingPathComponent(String(format: "frame-%02d.png", second))
        try bitmap.representation(using: .png, properties: [:])!.write(to: output)
        print(output.path)
    } catch {
        print("second \(second): \(error)")
    }
}
