import { pdfjs } from 'react-pdf';

// Configure PDF worker
// Using CDN to fetch the worker file matching the pdfjs-dist version used by react-pdf
// This avoids webpack configuration issues with worker-loader
if (pdfjs) {
  pdfjs.GlobalWorkerOptions.workerSrc = `//unpkg.com/pdfjs-dist@${pdfjs.version}/build/pdf.worker.min.js`;
  console.log('PDF Worker initialized', pdfjs.version);
}
